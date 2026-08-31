package com.systar.server.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.systar.system.entity.SysOperLogEntity;
import com.systar.system.service.SysOperLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

@Aspect
@Component
public class OperLogAspect {

    private static final Logger log = LoggerFactory.getLogger(OperLogAspect.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SysOperLogService sysOperLogService;

    public OperLogAspect(SysOperLogService sysOperLogService) {
        this.sysOperLogService = sysOperLogService;
    }

    @Around("@annotation(operLog)")
    public Object logOperation(ProceedingJoinPoint joinPoint,
                                OperLog operLog) throws Throwable {
        long start = System.currentTimeMillis();
        Object result;
        String errorMsg = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable e) {
            errorMsg = e.getMessage();
            throw e;
        } finally {
            try {
                SysOperLogEntity entity = new SysOperLogEntity();
                entity.setOperation(operLog.value());
                entity.setMethod(joinPoint.getSignature().toShortString());
                entity.setParams(truncate(MAPPER.writeValueAsString(joinPoint.getArgs()), 2000));
                entity.setCostTime(System.currentTimeMillis() - start);
                entity.setOperTime(LocalDateTime.now());
                if (errorMsg != null) {
                    entity.setErrorMsg(truncate(errorMsg, 2000));
                } else {
                    entity.setResult("success");
                }
                SystarUser user = SystarSecurityContext.get();
                if (user != null) {
                    entity.setUserId(user.getUserId());
                    entity.setUsername(user.getUsername());
                }
                ServletRequestAttributes attrs = (ServletRequestAttributes)
                        RequestContextHolder.getRequestAttributes();
                if (attrs != null) {
                    entity.setIp(attrs.getRequest().getRemoteAddr());
                }
                sysOperLogService.saveLog(entity);
            } catch (Exception e) {
                log.warn("Failed to save operation log: {}", e.getMessage());
            }
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}

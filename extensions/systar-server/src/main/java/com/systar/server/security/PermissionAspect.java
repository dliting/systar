package com.systar.server.security;

import com.systar.common.api.Result;
import com.systar.common.security.RequirePermission;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class PermissionAspect {

    private static final Logger log = LoggerFactory.getLogger(PermissionAspect.class);

    @Around("@annotation(requirePermission)")
    public Object checkPermission(ProceedingJoinPoint joinPoint,
                                   RequirePermission requirePermission) throws Throwable {
        SystarUser user = SystarSecurityContext.get();
        if (user == null) {
            log.warn("No authenticated user for permission check: {}",
                    requirePermission.value());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(Result.CODE_FORBIDDEN, "Access denied"));
        }

        if (!user.hasPermission(requirePermission.value())) {
            log.warn("User {} lacks permission: {}",
                    user.getUsername(), requirePermission.value());
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Result.error(Result.CODE_FORBIDDEN, "Insufficient permissions"));
        }

        return joinPoint.proceed();
    }
}

# Third-Party Notices

Systar is licensed under the GNU General Public License v3.0 only ([LICENSE](LICENSE)).
This file lists the third-party components Systar depends on and redistributes, together
with their licenses, so that recipients can comply with the respective terms.

This notice is informational. For the exact legally binding terms, always refer to the
license text of each component (linked below).

## 1. Vendored Maven artifacts (`lib/maven-repo/`)

The following artifacts are not available on Maven Central and are therefore vendored in
the repository to support offline / intranet builds. The upstream JARs contain no embedded
license file; the license statements below are taken from the upstream sources cited.

| Artifact | Version | License | Upstream source |
|----------|---------|---------|-----------------|
| `com.serotonin:bacnet4j` | 4.1.6 | GPL-3.0 (dual-licensed; a commercial license is also offered) | https://github.com/RadixIoT/BACnet4J |
| `com.lohbihler:sero-scheduler` | 1.1.0 | MIT © 2017 Matthew Lohbihler | https://github.com/mlohbihler/sero-scheduler |
| `com.lohbihler:sero-warp` | 1.0.0 | *license not declared upstream* | distributed via the MangoAutomation repository `maven.mangoautomation.net/repository/ias-release/` (published May 2017) |

**sero-warp note**: neither the published POM nor the JAR declares license terms, and no
public source repository for this artifact could be located. It is a small runtime utility
by the same author as sero-scheduler (Matthew Lohbihler), pulled in transitively by
BACnet4J. If you are the copyright holder and would like the attribution above corrected,
please open an issue.

**Maven Wrapper** (`.mvn/`, `mvnw`): Apache-2.0 — https://maven.apache.org/

## 2. Direct dependencies from Maven Central

Build and application framework:

| Component | Version | License |
|-----------|---------|---------|
| Spring Boot starters (`web`, `websocket`, `aop`, `cache`, `test`) | 3.3.6 | Apache-2.0 |
| Spring Security Crypto | 6.x (from Spring Boot BOM) | Apache-2.0 |
| MyBatis-Plus Boot Starter | 3.5.7 | Apache-2.0 |
| Hutool | 5.8.27 | MulanPSL-2.0 |
| Caffeine | 3.x (from Spring Boot BOM) | Apache-2.0 |
| Lombok | 1.18.x | MIT |
| SLF4J | 2.x | MIT |
| io.jsonwebtoken:jjwt | 0.9.1 | Apache-2.0 |
| javax.xml.bind:jaxb-api | 2.3.1 | CDDL 1.1 |

Databases:

| Component | Version | License |
|-----------|---------|---------|
| H2 | 2.3.232 | EPL 1.0 / MPL 2.0 (dual license) |
| MySQL Connector/J | 8.x (from Spring Boot BOM, runtime scope) | GPL-2.0 with FOSS Exception |

Protocol drivers:

| Component | Version | License |
|-----------|---------|---------|
| org.snmp4j:snmp4j | 3.7.7 | Apache-2.0 |
| org.eclipse.milo:sdk-client (OPC UA) | 0.6.8 | EPL-2.0 |
| com.github.s7connector:s7connector (Siemens S7) | 2.1 | Apache-2.0 |
| org.openmuc:j60870 (IEC 60870-5-104) | 1.2.0 | GPL-3.0 |
| org.eclipse.paho:org.eclipse.paho.client.mqttv3 | 1.2.5 | EPL-2.0 |
| org.java-websocket:Java-WebSocket | 1.5.4 | MIT |

Utilities:

| Component | Version | License |
|-----------|---------|---------|
| org.json:json | 20231013 | JSON License ("The Software shall be used for Good, not Evil") |
| org.dom4j:dom4j | 2.1.4 | BSD-style (declared "Plexus" in the upstream POM; see https://github.com/dom4j/dom4j/blob/master/LICENSE) |
| com.github.oshi:oshi-core | 6.6.1 | MIT |

Test scope only:

| Component | Version | License |
|-----------|---------|---------|
| Mockito | 5.14.2 | MIT |
| AssertJ | 3.26.3 | Apache-2.0 |
| Awaitility | 4.2.2 | Apache-2.0 |
| JUnit 5 (via spring-boot-starter-test) | 5.10.x | EPL-2.0 |

Transitive dependencies are not enumerated here; their license metadata resolves through
Maven Central.

**GPL-family components**: Systar itself is GPL-3.0-only, which is compatible with the
GPL-3.0 licensed j60870 driver and with MySQL Connector/J (GPL-2.0 + FOSS Exception,
invoked as an unmodified runtime JDBC driver).

## 3. Frontend npm dependencies (`frontend/`)

| Package | License |
|---------|---------|
| vue, vue-router, pinia, axios, element-plus, @element-plus/icons-vue | MIT |
| echarts | Apache-2.0 |
| vite, vitest, @vitejs/plugin-vue, @vue/test-utils, jsdom, sass (dev) | MIT |

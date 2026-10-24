# Third-Party Notices

Systar is licensed under the MIT License ([LICENSE](LICENSE)).
This file lists the third-party components Systar depends on and redistributes, together
with their licenses, so that recipients can comply with the respective terms.

This notice is informational. For the exact legally binding terms, always refer to the
license text of each component (linked below).

**Optional GPL module**: BACnet and IEC 60870-5-104 protocol support is provided by a
separate GPL-3.0-licensed module (`gpl/systar-drivers-gpl`) that is **not part of this
repository**. It links BACnet4J (GPL-3.0, dual-licensed by Radix IoT) and j60870
(GPL-3.0, openmuc). Binaries built with that module follow GPL-3.0 for the combined work;
everything in this repository stays MIT.

## 1. Vendored Maven artifacts (`lib/maven-repo/`)

Artifacts that are not available on Maven Central and are vendored in the repository to
support offline / intranet builds. The GPL-licensed artifacts (BACnet4J and its
`com.lohbihler` transitive dependencies) live in the local GPL build environment only and
are not redistributed in this repository.

| Artifact | Version | License | Upstream source |
|----------|---------|---------|-----------------|
| `com.serotonin:bacnet4j` | 4.1.6 | GPL-3.0 (dual-licensed; a commercial license is also offered) — local GPL module only, not redistributed here | https://github.com/RadixIoT/BACnet4J |
| `com.lohbihler:sero-scheduler` | 1.1.0 | MIT © 2017 Matthew Lohbihler — transitive dependency of BACnet4J, local GPL module only | https://github.com/mlohbihler/sero-scheduler |
| `com.lohbihler:sero-warp` | 1.0.0 | *license not declared upstream* — transitive dependency of BACnet4J, local GPL module only | distributed via the MangoAutomation repository `maven.mangoautomation.net/repository/ias-release/` (published May 2017) |

**sero-warp note**: neither the published POM nor the JAR declares license terms, and no
public source repository for this artifact could be located. It is a small runtime utility
by the same author as sero-scheduler (Matthew Lohbihler), pulled in transitively by
BACnet4J. It is consumed only by the local GPL module; this repository does not
redistribute it. If you are the copyright holder and would like the attribution above
corrected, please open an issue.

**Maven Wrapper** (`.mvn/`, `mvnw`): Apache-2.0 — https://maven.apache.org/

## 2. Direct dependencies from Maven Central

Build and application framework:

| Component | Version | License |
|-----------|---------|---------|
| Spring Boot starters (`web`, `websocket`, `aop`, `cache`, `test`) | 3.3.6 | Apache-2.0 |
| Spring Security Crypto | 6.x (from Spring Boot BOM) | Apache-2.0 |
| Jackson (`jackson-databind`) | 2.x (from Spring Boot BOM) | Apache-2.0 |
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
| MySQL Connector/J | 8.x (from Spring Boot BOM, runtime scope) | GPL-2.0 WITH Universal-FOSS-Exception-1.0 (see note below) |

**MySQL Connector/J note**: MySQL Connector/J 8.x is licensed `GPL-2.0 WITH
Universal-FOSS-Exception-1.0`. The FOSS Exception permits combining and distributing the
driver with software licensed under OSI-approved licenses (MIT qualifies) when the
combined work is distributed together with its complete source code — which this
repository satisfies. The exception applies to the unmodified driver used as a runtime
JDBC dependency; if you modify MySQL Connector/J itself, its GPL-2.0 terms govern your
modifications. Alternative engines (e.g. H2) are available if your distribution model
cannot meet these conditions.

Protocol drivers (this repository):

| Component | Version | License |
|-----------|---------|---------|
| org.snmp4j:snmp4j | 3.7.7 | Apache-2.0 |
| org.eclipse.milo:sdk-client (OPC UA) | 0.6.8 | EPL-2.0 |
| com.github.s7connector:s7connector (Siemens S7) | 2.1 | Apache-2.0 |
| org.eclipse.paho:org.eclipse.paho.client.mqttv3 | 1.2.5 | EPL-2.0 |
| org.java-websocket:Java-WebSocket | 1.5.4 | MIT |

BACnet (BACnet4J) and IEC 60870-5-104 (j60870) drivers are GPL-3.0 and ship in the
separate optional module described in the preamble — not among this repository's
dependencies.

Utilities:

| Component | Version | License |
|-----------|---------|---------|
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

## 3. Frontend npm dependencies (`frontend/`)

| Package | License |
|---------|---------|
| vue, vue-router, pinia, axios, element-plus, @element-plus/icons-vue | MIT |
| echarts | Apache-2.0 |
| vite, vitest, @vitejs/plugin-vue, @vue/test-utils, jsdom, sass (dev) | MIT |

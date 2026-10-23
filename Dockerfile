# Stage 1: build
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN ./gradlew --no-daemon bootJar

# Stage 2: runtime image on Eclipse OpenJ9 (IBM Semeru Runtimes), which needs less memory than HotSpot.
# Pinned tag, published 2026-09-18 (D-001).
FROM ibm-semeru-runtimes:open-21.0.12.10-jre-noble
WORKDIR /app
RUN useradd --system --uid 1001 ottos
COPY --from=build /app/build/libs/ottos-backend-*.jar app.jar
USER ottos
EXPOSE 8080
# OpenJ9 options live here, not in JAVA_TOOL_OPTIONS: the hosting sets that variable (heap size), which replaces the
# one the base image ships. HotSpot -XX: options in it are ignored by OpenJ9.
# - Xshareclasses: read-only cache of JDK classes that comes prebuilt in the base image (faster start, less memory).
# - PortableSharedCache: matches how that cache was built, so its precompiled code is used.
# - Xtune:virtualized: a less aggressive JIT, for low CPU and memory on shared cloud hosts.
# - IdleTuningGcOnIdle: when the app is idle, compact the heap and give the free memory back to the system.
ENTRYPOINT ["java", \
    "-Xshareclasses:name=openj9_system_scc,cacheDir=/opt/java/.scc,readonly,nonFatal", \
    "-XX:+PortableSharedCache", \
    "-Xtune:virtualized", \
    "-XX:+IdleTuningGcOnIdle", \
    "-XX:MaxRAMPercentage=75", \
    "-jar", "/app/app.jar"]

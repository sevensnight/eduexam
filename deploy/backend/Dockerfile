FROM public.ecr.aws/docker/library/eclipse-temurin:17-jre-jammy

RUN groupadd --system --gid 10001 eduexam \
    && useradd --system --uid 10001 --gid eduexam --home-dir /opt/eduexam --shell /usr/sbin/nologin eduexam

WORKDIR /opt/eduexam
COPY --chown=eduexam:eduexam app.jar app.jar

USER eduexam
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/opt/eduexam/app.jar"]

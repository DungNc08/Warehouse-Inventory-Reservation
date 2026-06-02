FROM liquibase/liquibase:4.27

WORKDIR /liquibase

COPY changelog ./changelog
COPY liquibase.properties .

ENTRYPOINT ["/liquibase/docker-entrypoint.sh"]
CMD ["--defaultsFile=/liquibase/liquibase.properties", "update"]

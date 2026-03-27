.PHONY: deploy

MAVEN_OPTS=--add-opens=java.base/java.util=ALL-UNNAMED\
	--add-opens=java.base/java.lang.reflect=ALL-UNNAMED\
	--add-opens=java.base/java.text=ALL-UNNAMED\
	--add-opens=java.desktop/java.awt.font=ALL-UNNAMED

deploy:
	MAVEN_OPTS="${MAVEN_OPTS}" GNUPGHOME="$(CURDIR)/.keys/gpg-home" ./mvnw clean deploy -Prelease

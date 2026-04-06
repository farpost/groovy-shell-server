.PHONY: deploy

MAVEN_OPTS=--add-opens=java.base/java.util=ALL-UNNAMED\
	--add-opens=java.base/java.lang.reflect=ALL-UNNAMED\
	--add-opens=java.base/java.text=ALL-UNNAMED\
	--add-opens=java.desktop/java.awt.font=ALL-UNNAMED

deploy:
	rm -rf /tmp/gpg-deploy && cp -r "$(CURDIR)/.keys/gpg-home" /tmp/gpg-deploy && chmod 700 /tmp/gpg-deploy
	MAVEN_OPTS="${MAVEN_OPTS}" GNUPGHOME="/tmp/gpg-deploy" ./mvnw clean deploy -Prelease -s .mvn/settings.xml
	rm -rf /tmp/gpg-deploy

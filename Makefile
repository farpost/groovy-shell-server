.PHONY: deploy

MAVEN_OPTS=--add-opens=java.base/java.util=ALL-UNNAMED\
	--add-opens=java.base/java.lang.reflect=ALL-UNNAMED\
	--add-opens=java.base/java.text=ALL-UNNAMED\
	--add-opens=java.desktop/java.awt.font=ALL-UNNAMED

# CalVer date-based version. Padded format (%m/%d) is portable across GNU/BSD date.
# GPG home is copied to /tmp because gpg-agent's Unix socket path has a ~108 char limit
# and a long $(CURDIR) path can exceed it.
deploy:
	rm -rf /tmp/gpg-deploy && cp -r "$(CURDIR)/.keys/gpg-home" /tmp/gpg-deploy && chmod 700 /tmp/gpg-deploy
	VERSION=$$(date +%Y.%m.%d.%H%M) && \
		MAVEN_OPTS="${MAVEN_OPTS}" GNUPGHOME="/tmp/gpg-deploy" ./mvnw clean deploy -Prelease -s .mvn/settings.xml -Drevision=$$VERSION
	rm -rf /tmp/gpg-deploy

# Releasing WildFly Glow

* `mvn versions:set -DnewVersion=1.0.0.Alpha1`
* `mvn versions:commit`
* `mvn clean install`
* `cp docs/target/generated-docs/index.html docs/index.html`
* `git add *`
* `git commit -m "Release version 1.0.0.Alpha1"`
* Deploy in nexus staging repository
** `mvn -Pjboss-release -Pjboss-staging-deploy deploy -DskipTests`
* Check that all is correct in https://repository.jboss.org/nexus/#browse/browse:wildfly-staging
* `git tag 1.0.0.Alpha1`
* Deploy to nexus release repository
** `mvn -Pjboss-staging-move nxrm3:staging-move`
* `git push upstream 1.0.0.Alpha1`
* Create a release from the tag and upload `cli/target/wildfly-glow-1.0.0.Alpha1.zip`
* `mvn versions:set -DnewVersion=1.0.0.Alpha2-SNAPSHOT`
* `mvn versions:commit`
* `git add <all updated pom files>`
* `git commit -m "New development iteration 1.0.0.Alpha2-SNAPSHOT"`
* `git push upstream main`
* DONE

# Releasing WildFly Glow

* `mvn versions:set -DnewVersion=1.0.0.Alpha1`
* `mvn versions:commit`
* `mvn clean install`
* `cp docs/target/generated-docs/index.html docs/index.html`
* `git add *`
* `git commit -m "Release version 1.0.0.Alpha1"`
* `mvn clean deploy`
* Release in nexus: https://repository.jboss.org/nexus
* `git tag 1.0.0.Alpha1`
* `git push upstream 1.0.0.Alpha1`
* Create a release from the tag and upload `cli/target/wildfly-glow-1.0.0.Alpha1.zip`
* `mvn versions:set -DnewVersion=1.0.0.Alpha2-SNAPSHOT`
* `mvn versions:commit`
* `git add <all updated pom files>`
* `git commit -m "New development iteration 1.0.0.Alpha2-SNAPSHOT"`
* `git push upstream main`
* DONE

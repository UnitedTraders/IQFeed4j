@Library('jenkins-helper') _

node('k8s') {
    try {

        branchName = env.BRANCH_NAME

        checkoutSourcesDocker()

        buildLibraryDocker([gitBranch: branchName, mavenBuilderImageName: 'maven-builder2-jdk21'])

        generateChangelog(branchName)

        deleteMerged(branchName)

    } catch (e) {

        reportFailedBuild(e)

    }
}
import {Octokit} from "octokit";
import {execSync} from "child_process";
import {readFile, writeFile} from "fs/promises";

const githubToken = process.argv[2];
const version = process.argv[3];
const releaseDate = process.argv[4];
const amountOfVersions = process.argv[5];
const branchToMerge = process.argv[6];

(async function () {
    let convChange = `git fetch --unshallow --tags origin ${branchToMerge} --quiet && git checkout origin/${branchToMerge} --quiet && conventional-changelog -r ${amountOfVersions}`;

    const changes = execSync(convChange).toString();
    console.log("printing out checkout changes from execSync: \n" + changes)

    const lines = changes.split(/\r?\n/);
    const addedFeatures = lines.filter(line => {
        return line.startsWith("* feat:")
    }).map(line => {
        return line.replace("* feat:", "* Feature: ")
    }).join("\n");

    const addedFixes = lines.filter(line => {
        return line.startsWith("* fix:");
    }).map(line => {
        return line.replace("* fix:", "* Bugfix: ")
    }).join("\n");

    const currentChangelog = await readFile("../../CHANGELOG.md");
    const changeLogLines = currentChangelog.toString().split(/\r?\n/)

    changeLogLines.shift();
    changeLogLines.shift();
    changeLogLines.shift();
    changeLogLines.shift();
    const restOfChangelog = changeLogLines.join("\n");

    console.log(addedFeatures)
    console.log(addedFixes)

    const changelogToStore = `# API Mediation Layer Changelog

All notable changes to the Zowe API Mediation Layer package will be documented in this file.

## \`${version} (${releaseDate})\`

${addedFeatures}

${addedFixes}

${restOfChangelog}`;

    await writeFile('../../CHANGELOG.md', changelogToStore);

    const octokit = new Octokit({auth: githubToken});
    const branch = `apiml/release/${version.replace(/\./g, "_")}`;

    let gitCommitPush = `git branch ${branch} && git checkout ${branch} && git add CHANGELOG.md && git commit --signoff -m "Update changelog" && git push origin ${branch}`;
    execSync(gitCommitPush, {
        cwd: '../../'
    });

    await octokit.rest.pulls.create({
        owner: 'zowe',
        repo: 'api-layer',
        title: 'Automatic update for the Changelog for release',
        head: branch,
        base: branchToMerge,
        body: 'Update changelog for new release'
    });
})()


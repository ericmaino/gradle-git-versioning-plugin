package me.qoomon.gradle.gitversioning;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testkit.runner.TaskOutcome;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.eclipse.jgit.lib.Constants.MASTER;
import static org.gradle.util.GFileUtils.writeFile;

class GitVersioningPluginTest {

    @TempDir
    Path projectDir;

    @Test
    void runVersionTask() throws GitAPIException {
        // given
        Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();

        File buildFile = projectDir.resolve("build.gradle").toFile();
        String givenVersion = "1.2.3";
        writeFile("plugins { id 'me.qoomon.git-versioning' }\nversion = '" + givenVersion + "'", buildFile);

        // when
        BuildResult buildresult = GradleRunner.create()
                .withProjectDir(projectDir.toFile())
                .withPluginClasspath()
                .withArguments("version", "-q")
                .build();

        // then
        assertThat(buildresult.task(":version")).satisfies(it ->
                assertThat(it.getOutcome()).isEqualTo(TaskOutcome.SUCCESS)
        );
        assertThat(buildresult.getOutput()).isEqualTo(givenVersion + System.lineSeparator());
    }

    @Test
    void apply() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        RevCommit commit = git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            rev = new PatchDescription() {{
                version = "${commit}";
            }};
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo(commit.getName());
    }

    @Test
    void apply_with_extension_commit_description() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "branch-gitVersioning";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("branch-gitVersioning");
    }

    @Test
    void apply_with_extension_branch_description() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenBranch = "feature/sandbox";
        git.branchCreate().setName(givenBranch).call();
        git.checkout().setName(givenBranch).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "${ref}-gitVersioning";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo(givenBranch.replace("/", "-") + "-gitVersioning");
    }

    @Test
    void apply_with_extension_tag_description() throws GitAPIException, IOException {

        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "v1";
        git.tag().setName(givenTag).call();
        git.checkout().setName(givenTag).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.tag(".*", patch -> {
                patch.version = "${ref}-gitVersioning";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo(givenTag + "-gitVersioning");
    }

    @Test
    void apply_normalizeVersion() throws GitAPIException, IOException {

        // given
        Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "a/b/c";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("a-b-c");
    }

    @Test
    void apply_emptyRepoGivesExpectedPlusDistanceResult() throws GitAPIException, IOException {
        // This can only be a local build so the specifics of the build number don't really matter, but 0 makes reasonable sense
        // given
        Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "${describe.tag.version.label.plus.describe.distance}";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("0");
    }

    @Test
    void apply_singleCommitUntaggedRepoGivesExpectedPlusDistanceResult() throws GitAPIException, IOException {
        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "${describe.tag.version.label.plus.describe.distance}";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("1");
    }

    @Test
    void apply_NoCommitsSinceLastTagGivesExpectedPlusDistanceResult() throws GitAPIException, IOException {
        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "2.0.4-677";
        git.tag().setName(givenTag).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "${describe.tag.version.label.plus.describe.distance}";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("677");
    }
    @Test
    void apply_TwoCommitsSinceLastTagGivesExpectedPlusDistanceResult() throws GitAPIException, IOException {
        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "2.0.4-677";
        git.tag().setName(givenTag).call();
        git.commit().setMessage("commit two").setAllowEmpty(true).call();
        git.commit().setMessage("commit three").setAllowEmpty(true).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "${describe.tag.version.label.plus.describe.distance}";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("679");
    }

    @Test
    void apply_CommitWithLabelTagGivesExpectedLabel() throws GitAPIException, IOException {
        // given
        Git git = Git.init().setInitialBranch(MASTER).setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();
        String givenTag = "2.0.4-677-SNAPSHOT";
        git.tag().setName(givenTag).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            refs.branch(".*", patch -> {
                patch.version = "${describe.tag.version.label}";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("677-SNAPSHOT");
    }

    @Test
    void apply_Issue90NullPointerWhenNoTagMatches() throws GitAPIException, IOException {
        // given
        Git git = Git.init().setInitialBranch("feature/fix-bug").setDirectory(projectDir.toFile()).call();
        git.commit().setMessage("initial commit").setAllowEmpty(true).call();

        Project project = ProjectBuilder.builder().withProjectDir(projectDir.toFile()).build();

        project.getPluginManager().apply(GitVersioningPlugin.class);

        GitVersioningPluginExtension extension = (GitVersioningPluginExtension) project.getExtensions()
                .getByName("gitVersioning");

        GitVersioningPluginConfig config = new GitVersioningPluginConfig() {{
            describeTagPattern = "v(?<version>\\d+\\.\\d+\\.\\d+)";
            refs.branch("feature/(?<text>.+)", patch -> {
                patch.version = "${describe.tag.version.major:-0}.${describe.tag.version.minor.next:-0}.0-${ref.text.slug}";
            });
        }};

        // when
        extension.apply(config);

        // then
        assertThat(project.getVersion()).isEqualTo("0.1.0-fix-bug");
    }
}
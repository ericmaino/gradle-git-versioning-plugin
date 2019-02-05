package me.qoomon.gitversioning;

import static java.util.stream.Collectors.toList;

import static me.qoomon.UncheckedExceptions.unchecked;

import java.util.List;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

public final class GitUtil {

    public static Status status(Repository repository) {
        return unchecked(() -> Git.wrap(repository).status().call());
    }

    public static String branch(Repository repository) {
        ObjectId head = unchecked(() -> repository.resolve(Constants.HEAD));
        if (head == null) {
            return Constants.MASTER;
        }
        String branch = unchecked(repository::getBranch);
        if (ObjectId.isId(branch)) {
            return null;
        }
        return branch;
    }

    public static List<String> tag_pointsAt(Repository repository, String revstr) {
        ObjectId rev = unchecked(() -> repository.resolve(revstr));
        return unchecked(() -> repository.getRefDatabase().getRefsByPrefix(Constants.R_TAGS)).stream()
                .map(ref -> unchecked(() -> repository.getRefDatabase().peel(ref)))
                .filter(ref -> (ref.isPeeled() ? ref.getPeeledObjectId() : ref.getObjectId()).equals(rev))
                .map(ref -> ref.getName().replaceFirst("^" + Constants.R_TAGS, ""))
                .collect(toList());
    }

    public static String revParse(Repository repository, String revstr) {
        ObjectId rev = unchecked(() -> repository.resolve(revstr));
        if (rev == null) {
            return "0000000000000000000000000000000000000000";
        }
        return rev.getName();
    }
}
package gitlet;

import java.io.*;
import java.util.*;

import static gitlet.Branches.*;
import static gitlet.Commit.*;
import static gitlet.Blob.*;
import static gitlet.MyUtils.*;
import static gitlet.Utils.*;
import static gitlet.Stage.*;

/**
 * Represents a Gitlet repository.
 * This class manages the overall version control process:
 * initialization, commits, branches, staging, checkout, reset, merge, and status.
 */
public class Repository {

    /** Standard Gitlet constants and key repository paths. */
    public static final int LONG_COMMIT_SIZE = 40;           // full SHA-1 length
    public static final int SHORT_COMMIT_SIZE = 8;           // abbreviated id length
    /** User working directory. */
    public static final File CWD = new File(System.getProperty("user.dir"));
    /** Path to .gitlet directory. */
    public static final File GITLET_DIR = join(CWD, ".gitlet");
    /** Objects directory storing commits and blobs. */
    public static final File OBJECT_DIR = join(GITLET_DIR, "objects");
    /** Serialized branches mapping file. */
    public static final File BRANCHES_FILE = join(GITLET_DIR, "branches");
    /** Serialized staging area file. */
    public static final File STAGE_FILE = join(GITLET_DIR, "stage");

    /** Repository state objects: branches, current commit, and stage. */
    private static Branches branches = loadBranches();
    private static Commit currCommit = readCommit(branches.getHeadId());
    private static Stage stage = loadStage();

    /** Verify that current directory contains a Gitlet system. */
    public static void checkGitLet() {
        if (!GITLET_DIR.exists()) {
            System.out.println("Not in an initialized Gitlet directory.");
            System.exit(0);
        }
    }

    /** Initialize a new Gitlet repository. */
    public static void init() {
        if (GITLET_DIR.exists()) {
            System.out.println("A Gitlet version-control system "
                    + "already exists in the current directory.");
            return;
        }
        currCommit = creInitCommit();                        // create initial commit
        branches = creBranches("master", currCommit.getId()); // create master branch
        stage = createStage();                                // initialize empty staging area
    }

    /** Add a file to the staging area for the next commit. */
    public static void add(String fileName) {
        if (!GITLET_DIR.exists()) {
            System.out.println("You haven't created a Gitlet System yet.");
            return;
        }
        File f = join(CWD, fileName);
        if (!f.exists()) {
            System.out.println("File does not exist.");
            return;
        }
        Blob blob = creBlob(f);           // create blob for this file
        stage.put(fileName, blob.getId());
    }

    /** Remove a file: either unstage it or mark it for deletion. */
    public static void rm(String fileName) {
        if (!stage.containFileName(fileName) && !currCommit.containFileName(fileName)) {
            System.out.println("No reason to remove the file.");
            return;
        }
        stage.remove(fileName);
        if (currCommit.containFileName(fileName)) {
            File f = join(CWD, fileName);
            deleteFile(f);
        }
    }

    /** Perform a commit operation using the current staging area. */
    public static void commit(String message) {
        branches.commit(message, stage);
    }

    /** Print commit history starting from current HEAD following first parent. */
    public static void log() {
        Commit p = currCommit;
        while (!p.getParents().isEmpty()) {
            System.out.println(p);
            String parentId = p.getParents().get(0);
            p = readCommit(parentId);
        }
        System.out.println(p);
    }

    /** Print all commits in the repository (unordered). */
    public static void globalLog() {
        List<Commit> commitList = getCommitList();
        for (Commit commit : commitList) {
            System.out.println(commit);
        }
    }

    /** Find commit ids whose messages match the given string. */
    public static void find(String message) {
        List<Commit> commitList = getCommitList();
        List<String> idList = new LinkedList<>();
        for (Commit commit : commitList) {
            if (commit.getMessage().equals(message)) {
                idList.add(commit.getId());
            }
        }
        printID(idList);
    }

    /** Helper: print commit IDs or show not found message. */
    private static void printID(List<String> idList) {
        if (idList.isEmpty()) {
            System.out.println("Found no commit with that message.");
        } else {
            for (String id : idList) {
                System.out.println(id);
            }
        }
    }

    /** Create a new branch pointing to current HEAD. */
    public static void branch(String branchName) {
        if (branches.containsBranch(branchName)) {
            System.out.println("A branch with that name already exists.");
            return;
        }
        branches.put(branchName, branches.getHeadId());
    }

    /** Delete an existing branch reference. */
    public static void rmBranch(String branchName) {
        if (branchName.equals(branches.getCurrBranchName())) {
            System.out.println("Cannot remove the current branch.");
            return;
        }
        if (!branches.containsBranch(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return;
        }
        branches.remove(branchName);
    }

    /**
     * Reset current branch to a given commit id.
     * Replaces working directory with that commit's snapshot.
     */
    public static void reset(String targetCommitId) {
        Commit targetCommit = readCommit(targetCommitId);
        if (targetCommit == null) {
            System.out.println("No commit with that id exists.");
            return;
        }
        // check for untracked file blockage
        if (!checkUntrackedConflicts(currCommit, targetCommit)) {
            return;
        }
        // replace entire working directory with target snapshot
        deleteAllFiles(CWD);
        for (String fileName : targetCommit.getFileNameSet()) {
            checkoutFileFromCommit(targetCommit.getId(), fileName);
        }
        stage.update(targetCommit);
        branches.put(branches.getCurrBranchName(), targetCommitId);
        branches.setHeadId(targetCommitId);
    }

    /** Checkout a single file from current commit. */
    public static void checkoutFileFrommCurrCommit(String fileName) {
        checkoutFileFromCommit(currCommit.getId(), fileName);
    }

    /** Checkout a single file from a specified commit. */
    public static void checkoutFileFromCommit(String targetCommitId, String fileName) {
        Commit targetCommit = readCommit(targetCommitId);
        if (targetCommit == null) {
            System.out.println("No commit with that id exists.");
            return;
        }
        if (!targetCommit.containFileName(fileName)) {
            System.out.println("File does not exist in that commit.");
            return;
        }
        String blobId = targetCommit.getBlobId(fileName);
        Blob blob = readBlob(blobId);
        blob.recover();
    }

    /** Switch working directory and HEAD to given branch. */
    public static void checkoutBranch(String branchName) {
        if (!branches.containsBranch(branchName)) {
            System.out.println("No such branch exists.");
            return;
        }
        if (branches.getCurrBranchName().equals(branchName)) {
            System.out.println("No need to checkout the current branch.");
            return;
        }
        Commit targetCommit = branches.getTipCommit(branchName);
        // check untracked file conflicts
        if (!checkUntrackedConflicts(currCommit, targetCommit)) {
            return;
        }
        // replace working directory with target
        deleteAllFiles(CWD);
        for (String fileName : targetCommit.getFileNameSet()) {
            checkoutFileFromCommit(targetCommit.getId(), fileName);
        }
        stage.update(targetCommit);
        branches.switchBranch(branchName);
    }



    /** Display the split (common ancestor) commit between two commits. */
    public static void split(String id1, String id2) {
        String splitCommitId = splitCommitId(id1, id2);
        System.out.println(readCommit(splitCommitId));
    }



    /** Read an object (Commit or Blob) by id and print its content. */
    public static void readObj(String id) {
        Serializable obj = readSerializable(id);
        if (obj instanceof Commit) {
            System.out.println(obj);
        }
        if (obj instanceof Blob) {
            System.out.println(obj);
        }
    }

    /** Read and print the current stage object file contents. */
    public static void readStage() {
        System.out.println(readObject(STAGE_FILE, Stage.class));
    }

    /**
     * Display repository status:
     * Shows branches, staged files, removed files, modifications not staged, and untracked files.
     */
    public static void statusS() {
        // === 1. Collect all relevant file names from CWD, stage, and current commit ===
        Set<String> allFiles = collectAllFileNames();

        // === 2. Analyze file statuses ===
        RepoStatus repoStatus = analyzeFileStatus(allFiles);

        // === 3. Print status sections ===
        printBranchesSection();
        printStatusSection("=== Staged Files ===", repoStatus.stagedFiles);
        printStatusSection("=== Removed Files ===", repoStatus.removedFiles);
        printStatusSection("=== Modifications Not Staged For Commit ===",
                repoStatus.modifiedNotStaged);
        printStatusSection("=== Untracked Files ===", repoStatus.untrackedFiles);
    }

    /**
     * Collect all filenames that should be considered when computing repository status.
     * Combines workspace files, files in stage area, and files in current commit.
     */
    private static Set<String> collectAllFileNames() {
        Set<String> fileNames = new HashSet<>(stage.getFileNameSet());

        List<String> cwdFiles = plainFilenamesIn(CWD);
        if (cwdFiles != null) {
            fileNames.addAll(cwdFiles);
        }
        fileNames.addAll(currCommit.getFileNameSet());
        return fileNames;
    }

    /**
     * Analyze each file to determine its category:
     * staged, removed, modified-not-staged, or untracked.
     */
    private static RepoStatus analyzeFileStatus(Set<String> fileNameSet) {
        List<String> staged = new LinkedList<>();
        List<String> removed = new LinkedList<>();
        List<String> modified = new LinkedList<>();
        List<String> untracked = new LinkedList<>();

        for (String fileName : fileNameSet) {
            Blob stagedBlob = readBlob(stage.getBlobId(fileName));
            Blob currBlob   = readBlob(currCommit.getBlobId(fileName));
            File cwdFile    = join(CWD, fileName);
            Blob cwdBlob    = cwdFile.exists() ? new Blob(cwdFile) : null;

            // 1. File is staged for addition
            if (!equalsBlob(stagedBlob, currBlob)
                    && equalsBlob(stagedBlob, cwdBlob) && cwdBlob != null) {
                staged.add(fileName);
            }

            // 2. File is marked for removal
            if (currCommit.containFileName(fileName)
                    && !stage.containFileName(fileName)) {
                removed.add(fileName);
            }

            // 3. Modified but not staged
            if (equalsBlob(stagedBlob, currBlob)
                    && !equalsBlob(stagedBlob, cwdBlob)) {
                if (currBlob != null && cwdBlob == null) {
                    modified.add(fileName + " (deleted)");
                } else if (stagedBlob != null && !equalsBlob(stagedBlob, cwdBlob)) {
                    modified.add(fileName + " (modified)");
                }
            }

            // 4. Untracked file (not in commit or stage)
            if (!currCommit.containFileName(fileName)
                    && !stage.containFileName(fileName)) {
                untracked.add(fileName);
            }
        }

        // Sort lists alphabetically for clean output
        staged.sort(String::compareTo);
        removed.sort(String::compareTo);
        modified.sort(String::compareTo);
        untracked.sort(String::compareTo);

        return new RepoStatus(staged, removed, modified, untracked);
    }

    /**
     * Print out the sections for branches.
     */
    private static void printBranchesSection() {
        System.out.println("=== Branches ===");
        System.out.println("*" + branches.getCurrBranchName());
        for (String branch : branches.getBranchNames()) {
            if (!branch.equals(branches.getCurrBranchName())) {
                System.out.println(branch);
            }
        }
        System.out.println();
    }

    /**
     * Print a titled section with the provided list of file names.
     */
    private static void printStatusSection(String title, List<String> items) {
        System.out.println(title);
        for (String name : items) {
            System.out.println(name);
        }
        System.out.println();
    }

    /**
     * A helper class to store categorized file lists for status output.
     */
    private static class RepoStatus {
        List<String> stagedFiles;
        List<String> removedFiles;
        List<String> modifiedNotStaged;
        List<String> untrackedFiles;

        RepoStatus(List<String> staged, List<String> removed,
                   List<String> modified, List<String> untracked) {
            this.stagedFiles = staged;
            this.removedFiles = removed;
            this.modifiedNotStaged = modified;
            this.untrackedFiles = untracked;
        }
    }

    /**
     * Merge the specified branch into the current branch.
     * Handles all cases: fast-forward, conflict, modification, and untracked file checks.
     */
    public static void merge(String branchName) {

        // === Step 1. Check preconditions ===
        if (!checkMergePreconditions(branchName)) {
            return;
        }

        // Load current, target, and split commit ids
        String currId = branches.getHeadId();
        String targetId = branches.getTipCommitId(branchName);
        String splitId = getSplitCommitId(currId, targetId, branchName);
        if (splitId == null) {
            return; // Already handled (fast-forward or ancestor)
        }

        // Load the corresponding commit objects
        Commit split = readCommit(splitId);
        Commit curr = currCommit;
        Commit target = branches.getTipCommit(branchName);

        // === Step 2. Check for untracked file conflicts ===
        if (!checkUntrackedConflicts(curr, target)) {
            return;
        }

        // === Step 3. Merge logic ===
        // Collect all candidate file names across three commits
        Set<String> allFiles = collectAllFiles(split, curr, target);
        boolean hasConflict = false;
        boolean hasChange = false;

        // Iterate each file and process merging logic
        for (String fileName : allFiles) {
            MergeResult r = mergeSingleFile(fileName, split, curr, target);
            if (r.hasConflict) {
                hasConflict = true;
            }
            if (r.hasChange) {
                hasChange = true;
            }
            if (r.mergedBlob != null) {
                // Place merged version into staging area
                stage.put(fileName, r.mergedBlob.getId());
            } else if (r.shouldDelete) {
                // Delete file if necessary
                deleteFile(join(CWD, fileName));
                stage.remove(fileName);
            }
        }

        if (!hasChange) {
            System.out.println("No changes to merge.");
            return;
        }

        // === Step 4. Finalize merge ===
        finalizeMerge(branchName, hasConflict);
    }

    /**
     * Checks preconditions for merging, including:
     * 1. Merging with self (not allowed)
     * 2. Existence of target branch
     * 3. Uncommitted changes in current branch
     */
    private static boolean checkMergePreconditions(String branchName) {
        if (branches.getCurrBranchName().equals(branchName)) {
            System.out.println("Cannot merge a branch with itself.");
            return false;
        }
        if (!branches.containsBranch(branchName)) {
            System.out.println("A branch with that name does not exist.");
            return false;
        }
        if (hasUncommittedChanges(currCommit, stage)) {
            System.out.println("You have uncommitted changes.");
            return false;
        }
        return true;
    }

    /**
     * Finds the split commit id between current and target branch.
     * Also handles special ancestor and fast-forward cases.
     */
    private static String getSplitCommitId(String currId, String targetId, String branchName) {
        String splitId = splitCommitId(currId, targetId);
        Commit split = readCommit(splitId);
        Commit curr = readCommit(currId);
        Commit target = readCommit(targetId);

        // If target branch is ancestor of current
        if (equalsCommit(split, target)) {
            System.out.println("Given branch is an ancestor of the current branch.");
            return null;
        }
        // If current branch is ancestor of target -> fast-forward
        if (equalsCommit(split, curr)) {
            System.out.println("Current branch fast-forwarded.");
            checkoutBranch(branchName);
            return null;
        }
        return splitId;
    }

    /**
     * Checks whether there are untracked files in working directory
     * that would be overwritten by files from the target commit.
     */
    private static boolean checkUntrackedConflicts(Commit curr, Commit target) {
        for (String fileName : plainFilenamesIn(CWD)) {
            if (!curr.containFileName(fileName) && target.containFileName(fileName)) {
                System.out.println("There is an untracked file in the way; "
                        + "delete it, or add and commit it first.");
                return false;
            }
        }
        return true;
    }

    /**
     * Collects all unique filenames that appear in any of
     * the split, current, or target commits.
     */
    private static Set<String> collectAllFiles(Commit split, Commit curr, Commit target) {
        Set<String> files = new HashSet<>();
        files.addAll(split.getFileNameSet());
        files.addAll(curr.getFileNameSet());
        files.addAll(target.getFileNameSet());
        return files;
    }

    /**
     * Handles merge logic for a single file based on comparison
     * between split, current, and target commits.
     * Returns a MergeResult describing necessary actions.
     */
    private static MergeResult mergeSingleFile(String fileName,
                                               Commit split, Commit curr, Commit target) {
        Blob splitBlob = readBlob(split.getBlobId(fileName));
        Blob currBlob = readBlob(curr.getBlobId(fileName));
        Blob targetBlob = readBlob(target.getBlobId(fileName));

        // Equality comparisons between file states
        boolean eqCurrSplit = equalsBlob(currBlob, splitBlob);
        boolean eqTarSplit = equalsBlob(targetBlob, splitBlob);
        boolean eqCurrTar  = equalsBlob(currBlob, targetBlob);

        MergeResult result = new MergeResult(fileName);

        // Case 1: Modified only in target branch
        if (eqCurrSplit && !eqTarSplit && targetBlob != null) {
            targetBlob.recover();
            result.setChange(targetBlob);
            return result;
        }

        // Case 2: Modified only in current branch
        if (!eqCurrSplit && eqTarSplit && currBlob != null) {
            result.setChange(currBlob);
            return result;
        }

        // Case 3: Modified in both but identical content
        if (!eqCurrSplit && !eqTarSplit && eqCurrTar && currBlob != null) {
            result.setChange(currBlob);
            return result;
        }

        // Case 4: Added only in current branch
        if (splitBlob == null && currBlob != null && targetBlob == null) {
            result.setChange(currBlob);
            return result;
        }

        // Case 5: Added only in target branch
        if (splitBlob == null && currBlob == null && targetBlob != null) {
            targetBlob.recover();
            result.setChange(targetBlob);
            return result;
        }

        // Case 6: Deleted only in target branch
        if (splitBlob != null && eqCurrSplit && targetBlob == null) {
            result.markDelete();
            return result;
        }

        // Case 7: Deleted only in current branch
        if (splitBlob != null && eqTarSplit && currBlob == null) {
            result.markDelete();
            return result;
        }

        // Case 8: Conflict - both changed differently
        if (!eqCurrTar) {
            String currContent   = currBlob != null ? currBlob.getContentAsString() : "";
            String targetContent = targetBlob != null ? targetBlob.getContentAsString() : "";

            // Construct conflict markers according to Git style
            String mergedText = "<<<<<<< HEAD\n"
                    + currContent
                    + "=======\n"
                    + targetContent
                    + ">>>>>>>\n";

            // Write conflict file and generate new blob
            File outFile = join(CWD, fileName);
            writeContents(outFile, mergedText);
            Blob mergedBlob = creBlob(outFile);
            result.setConflict(mergedBlob);
            return result;
        }

        return result; // Default: no change
    }

    /**
     * Creates a final merge commit including both parents,
     * updates branch pointers and stage area.
     */
    private static void finalizeMerge(String branchName, boolean hasConflict) {
        // Parent commits: current and target branch tips
        List<String> parents = Arrays.asList(currCommit.getId(),
                branches.getTipCommitId(branchName));
        // Standard merge commit message
        String msg = "Merged " + branchName + " into " + branches.getCurrBranchName() + ".";
        Commit mergeCommit = new Commit(msg, stage.getBlobIdMap(), parents);

        // Update branch head pointers and stage contents
        branches.setHeadId(mergeCommit.getId());
        branches.put(branches.getCurrBranchName(), mergeCommit.getId());
        stage.update(mergeCommit);

        if (hasConflict) {
            System.out.println("Encountered a merge conflict.");
        }
    }

    /**
     * Helper class that encapsulates the result of a single file merge.
     * Contains indicators for change, conflict, and deletion.
     */
    private static class MergeResult {
        String fileName;
        Blob mergedBlob;
        boolean hasChange = false;
        boolean hasConflict = false;
        boolean shouldDelete = false;

        MergeResult(String name) {
            this.fileName = name;
        }

        /** Marks file as changed and stores merged blob reference. */
        void setChange(Blob blob) {
            this.mergedBlob = blob;
            this.hasChange = true;
        }

        /** Marks file as conflicted and stores resulting blob. */
        void setConflict(Blob blob) {
            this.mergedBlob = blob;
            this.hasChange = true;
            this.hasConflict = true;
        }

        /** Marks file for deletion in working directory and stage. */
        void markDelete() {
            this.shouldDelete = true;
            this.hasChange = true;
        }
    }
}

package gitlet;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static gitlet.Utils.*;
import static gitlet.Repository.*;
import static gitlet.Commit.*;

/**
 * The Branches class keeps track of all branch names
 * and their corresponding tip commit ids.
 * Also records the current branch name and HEAD commit id.
 *
 * Serialized on disk in the BRANCHES_FILE.
 */
public class Branches implements Serializable {

    /** Mapping: branch name -> tip commit id. */
    private Map<String, String> branches;
    /** Name of the currently active branch. */
    private String currBranch;
    /** The current HEAD commit id. */
    private String HEAD;

    /**
     * Construct a new Branches object with a given current branch name.
     * Initially creates an empty branch map.
     */
    public Branches(String currBranch) {
        branches = new HashMap<>();
        this.currBranch = currBranch;
    }

    /**
     * Factory method to create and immediately save a Branches object.
     * Also sets the initial HEAD id and current branch mapping.
     */
    public static Branches creBranches(String currBranch, String headId) {
        Branches branches = new Branches(currBranch);
        branches.put(currBranch, headId);
        branches.setHEADId(headId);
        branches.save();
        return branches;
    }

    /** Get the current HEAD commit id. */
    public String getHEADId() {
        return HEAD;
    }

    /** Set HEAD commit id and persist the update. */
    public void setHEADId(String headId) {
        this.HEAD = headId;
        save();
    }

    /** Get name of the currently active branch. */
    public String getCurrBranchName() {
        return currBranch;
    }

    /** Get a set of all branch names. */
    public Set<String> getBranchNames() {
        return branches.keySet();
    }

    /** Set current branch name (does not update HEAD). */
    public void setCurrBranchName(String name) {
        currBranch = name;
    }

    /**
     * Switch to a different branch:
     * update current branch name and HEAD commit id.
     */
    public void switchBranch(String branchName) {
        setCurrBranchName(branchName);
        setHEADId(getTipCommitId(branchName));
        save();
    }

    /** Get the full mapping of branch name -> tip commit id. */
    public Map<String, String> getBranchesMap() {
        return branches;
    }

    /** Get the tip commit id of a particular branch. */
    public String getTipCommitId(String name) {
        return branches.get(name);
    }

    /** Get the tip Commit object of a particular branch. */
    public Commit getTipCommit(String branchName) {
        return readCommit(getTipCommitId(branchName));
    }

    /** Add or update a branch tip commit id, then save to disk. */
    public void put(String name, String commitId) {
        branches.put(name, commitId);
        save();
    }

    /** Check if a branch with the given name exists. */
    public boolean containsBranch(String name) {
        return branches.containsKey(name);
    }

    /** Remove a branch entry by name and save. */
    public void remove(String name) {
        branches.remove(name);
        save();
    }

    /** Save this Branches object to the BRANCHES_FILE. */
    public void save() {
        writeObject(BRANCHES_FILE, this);
    }

    /**
     * Load branches data from the BRANCHES_FILE.
     * If file does not exist, return a new Branches instance with master as default.
     */
    public static Branches loadBranches() {
        if (BRANCHES_FILE.exists()) {
            return readObject(BRANCHES_FILE, Branches.class);
        } else {
            return new Branches("master");
        }
    }

    /**
     * Create a new commit on the current branch with the given message and stage.
     * Updates both branch tip and HEAD to the new commit.
     */
    public void commit(String message, Stage stage) {
        // Validate commit message
        if (message == null || message.isEmpty()) {
            System.out.println("Please enter a commit message.");
            return;
        }

        // Create child commit from current HEAD
        Commit currCommit = readCommit(HEAD);
        Commit childCommit = currCommit.creChildCommit(message, stage);

        if (childCommit == null) {
            System.out.println("No changes added to the commit.");
            return;
        }

        // Update branch tip and HEAD to new commit
        branches.put(currBranch, childCommit.getId());
        setHEADId(childCommit.getId());
        //stage.clear();
        save();
    }

    /**
     * Printable representation of branches for status messages.
     * The current branch is marked with an asterisk (*).
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Branches ===").append("\n");
        sb.append("*").append(currBranch).append("\n");

        for (String branchName : branches.keySet()) {
            if (!branchName.equals(currBranch)) {
                sb.append(branchName).append("\n");
            }
        }
        sb.append("\n");
        return sb.toString();
    }
}

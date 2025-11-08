package gitlet;

import java.io.File;
import java.io.Serializable;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static gitlet.MyUtils.*;
import static gitlet.Utils.*;
import static gitlet.Repository.*;

/**
 * Represents a gitlet commit object.
 * A Commit records a snapshot of the file system at a certain point,
 * including commit message, timestamp, and references to parent commits.
 */
public class Commit implements Serializable {
    /** The message associated with this commit (description of the changes). */
    private String message;
    /** Mapping of file name -> Blob id (file snapshot identifiers). */
    private Map<String, String> blobIdMap = new HashMap<>();
    /** List of parent commit ids (normally one, two for merge commits). */
    private List<String> parents;
    /** Date object recording when this commit was created. */
    private Date currentTime;
    /** Unique id of this commit, computed by SHA-1. */
    private String id;
    /** Timestamp string version of the creation date. */
    private String timeStamp;

    /** Construct a normal (non‑initial) commit object. */
    public Commit(String message, Map<String, String> map, List<String> parents) {
        this.message = message;
        this.blobIdMap = map;
        this.parents = parents;
        this.currentTime = new Date();
        this.timeStamp = dateToTimeStamp(currentTime);
        this.id = generateID();
        saveCommit();  // deferred saving
    }

    /** Construct the initial commit object (the first commit). */
    public Commit() {
        this.currentTime = new Date(0);                    // epoch time
        this.timeStamp = dateToTimeStamp(this.currentTime);
        this.message = "initial commit";
        this.blobIdMap = new HashMap<>();
        this.parents = new LinkedList<>();
        this.id = generateID();
        //saveCommit();
    }

    /** Factory method: create and save initial commit. */
    public static Commit creInitCommit() {
        Commit commit = new Commit();
        commit.saveCommit();
        return commit;
    }

    /** Convert a Date to timestamp string used in commit metadata. */
    private static String dateToTimeStamp(Date date) {
        DateFormat dateFormat = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy Z", Locale.US);
        return dateFormat.format(date);
    }

    /** Generate commit id based on parent ids and message. */
    private String generateID() {
        return Utils.sha1(parents.toString(), message, "Commit");
        // alternate forms commented out
    }

    /** Get commit id. */
    public String getId() {
        return id;
    }

    /** Get commit message. */
    public String getMessage() {
        return message;
    }

    /** Get mapping (file name -> Blob id) stored in this commit. */
    public Map<String, String> getBlobIdMap() {
        return blobIdMap;
    }

    /** Get parent commit ids list. */
    public List<String> getParents() {
        return parents;
    }

    /** Add or update one file entry: file name -> blob id. */
    public void put(String fileName, String blobId) {
        blobIdMap.put(fileName, blobId);
    }

    /** Remove a file entry from this commit snapshot. */
    public void remove(String fileName) {
        blobIdMap.remove(fileName);
    }

    /** Save this commit object to repository object directory. */
    public void saveCommit() {
        // Ensure object directory exists
        if (!OBJECT_DIR.exists() && !OBJECT_DIR.mkdirs()) {
            throw new GitletException("can't create dir:" + OBJECT_DIR.getAbsolutePath());
        }
        File f = join(OBJECT_DIR, id);
        writeObject(f, this);
    }

    /** Check if the commit contains a specific blob id. */
    public boolean containBlob(String blobId) {
        return blobIdMap.containsValue(blobId);
    }

    /** Read a commit from disk by commit id; return null if not found or not Commit type. */
    public static Commit readCommit(String commitId) {
        if (commitId == null) {
            return null;
        }
        Serializable obj = readSerializable(commitId);
        return (obj instanceof Commit) ? (Commit) obj : null;
    }

    /** Get all file names tracked by this commit. */
    public Set<String> getFileNameSet() {
        return blobIdMap.keySet();
    }

    /** Check if a file name is tracked in this commit. */
    public boolean containFileName(String fileName) {
        return blobIdMap.containsKey(fileName);
    }

    /** Get blob id corresponding to a file name. */
    public String getBlobId(String fileName) {
        return blobIdMap.get(fileName);
    }

    /**
     * Create a child commit of the current commit.
     * Copies staging area contents, assigns as new commit's snapshot,
     * saves commit to disk, and returns it.
     */
    public Commit creChildCommit(String commitMessage, Stage stage) {
        if (stage.getBlobIdMap().equals(blobIdMap)) {
            // No modifications compared to current commit: nothing to commit
            return null;
        }
        List<String> commitParents = new LinkedList<>();
        commitParents.add(id);
        Map<String, String> tmpBlobIdMap = new HashMap<>();
        for (String fileName : stage.getFileNameSet()) {
            tmpBlobIdMap.put(fileName, stage.getBlobId(fileName));
        }
        Commit childCommit = new Commit(commitMessage, tmpBlobIdMap, commitParents);
        //childCommit.saveCommit();
        return childCommit;
    }

    /** Retrieve all commit objects present in the object directory. */
    public static List<Commit> getCommitList() {
        List<String> fileList = plainFilenamesIn(OBJECT_DIR);
        List<Commit> commitList = new LinkedList<>();
        if (fileList == null) {
            return commitList;
        }
        for (String fileName : fileList) {
            Commit commit = readCommit(fileName);
            if (commit != null) {
                commitList.add(commit);
            }
        }
        return commitList;
    }

    /** Update ancestor distance map with a new commit id and distance value. */
    private static void updateDistMap(Map<String, Integer> map, String id, int dist) {
        if (!map.containsKey(id)) {
            map.put(id, dist);
        } else if (dist < map.get(id)) {
            map.put(id, dist);
        }
    }

    /**
     * Collect all ancestor commits of current commit (id -> distance).
     * Distance indicates steps from this commit to each ancestor.
     */
    private Map<String, Integer> collectAncestors() {
        Map<String, Integer> distMap = new HashMap<>();
        LinkedList<String> deque = new LinkedList<>();
        deque.add(id);
        distMap.put(id, 0);
        while (!deque.isEmpty()) {
            String topCommitId = deque.removeFirst();
            Commit topCommit = readCommit(topCommitId);
            int preDist = distMap.get(topCommitId);
            for (String parentId : topCommit.getParents()) {
                if (!distMap.containsKey(parentId)) {
                    deque.addLast(parentId);
                    updateDistMap(distMap, parentId, preDist + 1);
                }
            }
        }
        return distMap;
    }

    /**
     * Find the nearest common ancestor (split point) between two commits.
     * Returns the commit id of that split point.
     */
    public static String splitCommitId(String id1, String id2) {
        Map<String, Integer> distMap1 = readCommit(id1).collectAncestors();
        Map<String, Integer> distMap2 = readCommit(id2).collectAncestors();

        Set<String> commonCommitIdSet = new HashSet<>(distMap1.keySet());
        commonCommitIdSet.retainAll(distMap2.keySet());

        String nearestCommiId = "";
        int distSum = Integer.MAX_VALUE;
        for (String commitId : commonCommitIdSet) {
            int dist1 = distMap1.get(commitId);
            int dist2 = distMap2.get(commitId);
            if (dist1 + dist2 < distSum) {
                distSum = dist1 + dist2;
                nearestCommiId = commitId;
            }
        }
        return nearestCommiId;
    }

    /**
     * Check if there are uncommitted changes between current commit and staging area,
     * including modified, added, or deleted files.
     */
    public static boolean hasUncommittedChanges(Commit currCommit, Stage stage) {
        Map<String, String> headMap = currCommit.getBlobIdMap();
        Map<String, String> stagedMap = stage.getBlobIdMap();

        // Difference between commit and staged snapshot
        if (!headMap.equals(stagedMap)) {
            return true;
        }

        // Check working directory for modified but unstaged files
        for (String name : plainFilenamesIn(CWD)) {
            File f = join(CWD, name);
            String fileSha = Utils.sha1(name, Utils.readContents(f), "Blob");
            String stagedBlobId = stagedMap.get(name);
            if (stagedBlobId != null && !fileSha.equals(stagedBlobId)) {
                return true;
            }
        }

        // Check for deleted tracked files
        for (String name : stagedMap.keySet()) {
            File f = join(CWD, name);
            if (!f.exists() && headMap.containsKey(name)) {
                return true;
            }
        }

        return false;
    }

    /** Compare two commits by id equality. */
    public static boolean equalsCommit(Commit c1, Commit c2) {
        if (c1 == null || c2 == null) {
            return false;
        }
        return c1.getId().equals(c2.getId());
    }

    /** Text representation for displaying commit information. */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("===\n");
        sb.append("commit ").append(id).append("\n");
        sb.append("Date: ").append(timeStamp).append("\n");
        sb.append(message).append("\n");
        return sb.toString();
    }
}

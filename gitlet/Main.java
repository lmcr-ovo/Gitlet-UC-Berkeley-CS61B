package gitlet;

/** Driver class for Gitlet, a subset of the Git version-control system.
 */
public class Main {

    /** Usage: java gitlet.Main ARGS, where ARGS contains
     *  <COMMAND> <OPERAND1> <OPERAND2> ... 
     */
    public static void main(String[] args) {
        //Repository repository = new Repository();

        if (args.length == 0) {
            System.out.println("Please enter a command.");
            return;
        }

        String firstArg = args[0];
        if (!firstArg.equals("init")) {
            Repository.checkGitLet();
        }
        switch (firstArg) {
            case "init":
                Repository.init();
                break;
            case "add":
                Repository.add(args[1]);
                break;
            case "commit":
                Repository.commit(args[1]);
                break;
            case "rm":
                Repository.rm(args[1]);
                break;
            case "branch":
                Repository.branch(args[1]);
                break;
            case "rm-branch":
                Repository.rmBranch(args[1]);
                break;
            case "reset":
                Repository.reset(args[1]);
                break;
            case "status":
                Repository.status();
                //Repository.saveStatusOutputToFile();
                break;
            case "log":
                Repository.log();
                break;
            case "global-log":
                Repository.globalLog();
                break;
            case "find":
                Repository.find(args[1]);
                break;
            case "split":
                Repository.split(args[1], args[2]);
                break;
            case "merge":
                Repository.merge(args[1]);
                break;
            case "readObj":
                Repository.readObj(args[1]);
                break;
            case "readStage":
                Repository.readStage();
                break;
            case "checkout":
                if (args.length == 3 && args[1].equals("--")) {
                    // checkout -- [file name]
                    Repository.checkoutFileFrommCurrCommit(args[2]);
                } else if (args.length == 4 && args[2].equals("--")) {
                    // checkout [commit id] -- [file name]
                    Repository.checkoutFileFromCommit(args[1], args[3]);
                } else if (args.length == 2) {
                    // checkout [branch name]
                    Repository.checkoutBranch(args[1]);
                } else {
                    System.out.println("Incorrect operands.");
                }
                break;
            default:
                System.out.println("No command with that name exists.");
        }

    }
}

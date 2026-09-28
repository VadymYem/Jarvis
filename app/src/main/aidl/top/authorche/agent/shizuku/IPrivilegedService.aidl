package top.authorche.agent.shizuku;

interface IPrivilegedService {
    String execute(in String[] command);
    int uid();
}

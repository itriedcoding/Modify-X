package dev.modifyx.core;

/**
 * Runs shell commands in a process started by Shizuku with the shell UID.
 * Replies with a JSON object: {"exit":Int,"out":String,"timeout":Boolean}.
 */
interface IUserService {
    String exec(String command, int timeoutMs);
    int version();
}

// IUserService.aidl
package com.unlock.shizuku;

interface IUserService {
    // Reserved transaction id used by the Shizuku server to tear the service down.
    void destroy() = 16777114;

    void exit() = 1;

    // Run a command as the shell (uid 2000) or root user that hosts this service.
    // `command` is an argv array, e.g. ["pm","uninstall","--user","0","com.foo"].
    String execute(in String[] command) = 2;
}

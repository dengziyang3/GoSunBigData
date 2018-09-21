/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.hzgc.collect.service.ftp.nativefs.filesystem.impl;

import com.hzgc.collect.config.CollectContext;
import com.hzgc.collect.service.ftp.ftplet.FileSystemView;
import com.hzgc.collect.service.ftp.ftplet.FtpFile;
import com.hzgc.collect.service.ftp.ftplet.User;
import com.hzgc.collect.service.ftp.nativefs.filesystem.NativeFileSystemFactory;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.util.List;
import java.util.StringTokenizer;

/**
 * <strong>Internal class, do not use directly.</strong>
 * <p>
 * File system view based on native file system. Here the root directory will be
 * user virtual root (/).
 *
 * @author <a href="http://mina.apache.org">Apache MINA Project</a>
 */
@Slf4j
public class NativeFileSystemView implements FileSystemView {
    private CollectContext collectContext;


    // the root directory will always end with '/'.
    private volatile String rootDir;

    // the first and the last character will always be '/'
    // It is always with respect to the root directory.
    private String currDir;

    private final User user;

    // private boolean writePermission;

    private final boolean caseInsensitive;

    /**
     * Constructor - internal do not use directly, use {@link NativeFileSystemFactory} instead
     */
    private NativeFileSystemView(User user, boolean caseInsensitive) {
        if (user == null) {
            throw new IllegalArgumentException("user can not be null");
        }
        if (user.getHomeDirectory() == null) {
            throw new IllegalArgumentException(
                    "User home directory can not be null");
        }

        this.caseInsensitive = caseInsensitive;

        // add last '/' if necessary
        String rootDir = user.getHomeDirectory();
        rootDir = NativeFtpFile.normalizeSeparateChar(rootDir);
        if (!rootDir.endsWith("/")) {
            rootDir += '/';
        }

        log.debug("Native filesystem view created for user \"{}\" with root \"{}\"", user.getName(), rootDir);

        this.rootDir = rootDir;

        this.user = user;

        currDir = "/";
    }

    public NativeFileSystemView(User user, boolean caseInsensitive, CollectContext collectContext) {
        this(user, caseInsensitive);
        this.collectContext = collectContext;
    }

    /**
     * Get the user home directory. It would be the file system root for the
     * user.
     */
    public FtpFile getHomeDirectory() {
        checkRootDir();
        return new NativeFtpFile("/", new File(rootDir), user);
    }

    /**
     * Get the current directory.
     */
    public FtpFile getWorkingDirectory() {
        checkRootDir();
        FtpFile fileObj;
        if (currDir.equals("/")) {
            fileObj = new NativeFtpFile("/", new File(rootDir), user);
        } else {
            File file = new File(rootDir, currDir.substring(1));
            fileObj = new NativeFtpFile(currDir, file, user);

        }
        return fileObj;
    }

    /**
     * Get file object.
     */
    public FtpFile getFile(String file) {
        checkRootDir();
        // get actual file object
        String physicalName = getPhysicalName(rootDir,
                currDir, file, caseInsensitive);
        File fileObj = new File(physicalName);
        File parentFile = fileObj.getParentFile();
        if (!parentFile.exists()) {
            boolean success = parentFile.mkdirs();
            if (success) {
                log.info("Create new directory: " + parentFile.getPath());
            }
        }
        // strip the root directory and return
        String userFileName = physicalName.substring(rootDir.length() - 1);
        return new NativeFtpFile(userFileName, fileObj, user);
    }

    private void checkRootDir() {
        if (!rootDir.equals(collectContext.getFtpHomeDir().getRootDir())) {
            user.setHomeDirectory(collectContext.getFtpHomeDir().getRootDir());
            this.rootDir = collectContext.getFtpHomeDir().getRootDir();
        }
    }

    public FtpFile getFile_RETR(String file) {
        checkRootDir();
        String physicalName = getPhysicalName(rootDir,
                currDir, file, caseInsensitive);
        File fileObj = new File(physicalName);
        String userFileName = physicalName.substring(rootDir.length() - 1);

        if (fileObj.isFile()) {
            return new NativeFtpFile(userFileName, fileObj, user);
        } else {
            List <String> ladenHomeDirs = collectContext.getFtpHomeDir().getLadenHomeDirs();
            if (ladenHomeDirs != null && ladenHomeDirs.size() > 0) {
                for (String otherRootDir : ladenHomeDirs) {
                    String otherPhysicalName = getPhysicalName(otherRootDir,
                            currDir, file, caseInsensitive);
                    File otherFileObj = new File(otherPhysicalName);
                    if (otherFileObj.isFile()) {
                        return new NativeFtpFile(userFileName, otherFileObj, user);
                    }
                }
            } else {
                return new NativeFtpFile(userFileName, fileObj, user);
            }
            return new NativeFtpFile(userFileName, fileObj, user);
        }
    }

    /**
     * Change directory.
     */
    public boolean changeWorkingDirectory(String dir) {
        checkRootDir();
        // not a directory - return false
        dir = getPhysicalName(rootDir, currDir, dir,
                caseInsensitive);
        File dirObj = new File(dir);
        if (!dirObj.isDirectory()) {
            return false;
        }

        // strip user root and add last '/' if necessary
        dir = dir.substring(rootDir.length() - 1);
        if (dir.charAt(dir.length() - 1) != '/') {
            dir = dir + '/';
        }

        currDir = dir;
        return true;
    }

    /**
     * Is the file content random accessible?
     */
    public boolean isRandomAccessible() {
        return true;
    }

    /**
     * Dispose file system view - does nothing.
     */
    public void dispose() {
    }

    /**
     * Get the physical canonical file name. It works like
     * File.getCanonicalPath().
     *
     * @param rootDir  The root directory.
     * @param currDir  The current directory. It will always be with respect to the
     *                 root directory.
     * @param fileName The input file name.
     * @return The return string will always begin with the root directory. It
     * will never be null.
     */
    private String getPhysicalName(final String rootDir,
                                   final String currDir, final String fileName,
                                   final boolean caseInsensitive) {

        // normalize root dir
        String normalizedRootDir = normalizeSeparateChar(rootDir);
        normalizedRootDir = appendSlash(normalizedRootDir);

        // normalize file name
        String normalizedFileName = normalizeSeparateChar(fileName);
        String result;

        // if file name is relative, set resArg to root dir + curr dir
        // if file name is absolute, set resArg to root dir
        if (normalizedFileName.charAt(0) != '/') {
            // file name is relative
            String normalizedCurrDir = normalize(currDir);

            result = normalizedRootDir + normalizedCurrDir.substring(1);
        } else {
            result = normalizedRootDir;
        }

        // strip last '/'
        result = trimTrailingSlash(result);

        // replace ., ~ and ..
        // in this loop resArg will never end with '/'
        StringTokenizer st = new StringTokenizer(normalizedFileName, "/");
        while (st.hasMoreTokens()) {
            String tok = st.nextToken();

            // . => current directory
            switch (tok) {
                case ".":
                    // ignore and move on
                    break;
                case "src/test":
                    // .. => parent directory (if not root)
                    if (result.startsWith(normalizedRootDir)) {
                        int slashIndex = result.lastIndexOf('/');
                        if (slashIndex != -1) {
                            result = result.substring(0, slashIndex);
                        }
                    }
                    break;
                case "~":
                    // ~ => home directory (in this case the root directory)
                    result = trimTrailingSlash(normalizedRootDir);
                    continue;
                default:
                    // token is normal directory name

                    if (caseInsensitive) {
                        // we're case insensitive, find a directory with the name, ignoring casing
                        File[] matches = new File(result)
                                .listFiles(new NameEqualsFileFilter(tok, true));

                        if (matches != null && matches.length > 0) {
                            // found a file matching tok, replace tok for get the right casing
                            tok = matches[0].getName();
                        }
                    }

                    result = result + '/' + tok;

                    break;
            }
        }

        // add last slash if necessary
        if ((result.length()) + 1 == normalizedRootDir.length()) {
            result += '/';
        }

        // make sure we did not end up above root dir
        if (!result.startsWith(normalizedRootDir)) {
            result = normalizedRootDir;
        }

        return result;
    }

    /**
     * Append trailing slash ('/') if missing
     */
    private String appendSlash(String path) {
        if (path.charAt(path.length() - 1) != '/') {
            return path + '/';
        } else {
            return path;
        }
    }

    /**
     * Prepend leading slash ('/') if missing
     */
    private String prependSlash(String path) {
        if (path.charAt(0) != '/') {
            return '/' + path;
        } else {
            return path;
        }
    }

    /**
     * Trim trailing slash ('/') if existing
     */
    private String trimTrailingSlash(String path) {
        if (path.charAt(path.length() - 1) == '/') {
            return path.substring(0, path.length() - 1);
        } else {
            return path;
        }
    }

    /**
     * Normalize separate character. Separate character should be '/' always.
     */
    private String normalizeSeparateChar(final String pathName) {
        String normalizedPathName = pathName.replace(File.separatorChar, '/');
        normalizedPathName = normalizedPathName.replace('\\', '/');
        return normalizedPathName;
    }

    /**
     * Normalize separator char, append and prepend slashes. Default to
     * defaultPath if null or empty
     */
    private String normalize(String path) {
        if (path == null || path.trim().length() == 0) {
            path = "/";
        }

        path = normalizeSeparateChar(path);
        path = prependSlash(appendSlash(path));
        return path;
    }
}

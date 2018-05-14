package com.constellio.data.dao.services.contents;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import com.constellio.data.io.streamFactories.impl.CopyInputStreamFactory;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Strings;
import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;

import com.constellio.data.conf.DataLayerConfiguration;
import com.constellio.data.conf.DigitSeparatorMode;
import com.constellio.data.dao.managers.StatefulService;
import com.constellio.data.dao.services.contents.ContentDaoException.ContentDaoException_NoSuchContent;
import com.constellio.data.dao.services.contents.ContentDaoRuntimeException.ContentDaoRuntimeException_CannotDeleteFolder;
import com.constellio.data.dao.services.contents.ContentDaoRuntimeException.ContentDaoRuntimeException_CannotMoveFolderTo;
import com.constellio.data.dao.services.contents.ContentDaoRuntimeException.ContentDaoRuntimeException_NoSuchFolder;
import com.constellio.data.io.services.facades.IOServices;
import com.constellio.data.io.streamFactories.CloseableStreamFactory;
import com.constellio.data.utils.ImpossibleRuntimeException;

public class FileSystemContentDao implements StatefulService, ContentDao {

	public static final String RECOVERY_FILE = "recoveryfile.bac";
	private static final String COPY_RECEIVED_STREAM_TO_FILE = "FileSystemContentDao-CopyReceivedStreamToFile";
	public static final String FILE_SYSTEM_CONTENT_DAO_FILE_STREAM_NAME = "fileSystemContentDaoFileStream";

	IOServices ioServices;

    @VisibleForTesting
	File rootFolder;

    File vaultRecoveryFile;

    @VisibleForTesting
    File replicatedRootFolder;

	File replicatedRootRecoveryFile = null;

	DataLayerConfiguration configuration;

	List<FileSystemContentDaoExternalResourcesExtension> externalResourcesExtensions = new ArrayList<>();

	public FileSystemContentDao(IOServices ioServices, DataLayerConfiguration configuration) {
		this.ioServices = ioServices;
		this.configuration = configuration;

		rootFolder = configuration.getContentDaoFileSystemFolder();
		vaultRecoveryFile = new File(rootFolder, RECOVERY_FILE);

		if(!vaultRecoveryFile.exists()) {
			try {
				if(!vaultRecoveryFile.createNewFile()) {
					throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToCreateVaultRecoveryFile(vaultRecoveryFile);
				}
			} catch (IOException e) {
				throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToCreateVaultRecoveryFile(vaultRecoveryFile);
			}
		}

		String replicatedVaultMountPoint = configuration.getContentDaoReplicatedVaultMountPoint();
        if (StringUtils.isNotBlank(replicatedVaultMountPoint)) {
            replicatedRootFolder = new File(replicatedVaultMountPoint);
			replicatedRootRecoveryFile = new File(replicatedRootFolder, RECOVERY_FILE);
			if(!replicatedRootRecoveryFile.exists()) {
				try {
					if(!replicatedRootRecoveryFile.createNewFile()) {
						throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToCreateReplicationRecoveryFile(replicatedRootRecoveryFile);
					}
				} catch (IOException e) {
					throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToCreateReplicationRecoveryFile(replicatedRootRecoveryFile);
				}
			}
        }
	}

	File getVaultRootRecoveryFile() {
		return vaultRecoveryFile;
	}

	File getReplicationRootRecoveryFile() {
		return replicatedRootRecoveryFile;
	}

	boolean fileCopy(File fileToCopy, String filePath) {
		boolean isFileToCoped;
		if(fileToCopy == null || Strings.isNullOrEmpty(filePath)) {
			return false;
		}

		try {
			if(!new File(filePath).exists()) {
				FileUtils.copyFile(fileToCopy, new File(filePath));
			}
			isFileToCoped = true;

		} catch (IOException e) {
			isFileToCoped = false;
		}

		return isFileToCoped;
	}

	boolean moveFile(File fileToBeMoved, File target) {
		boolean isFileMoved;

		if(fileToBeMoved == null || target == null) {
			return false;
		}

		try {
			FileUtils.moveFile(fileToBeMoved, target);
			isFileMoved = true;
		} catch (FileExistsException e) {
			isFileMoved = true;
			//OK
		} catch (IOException e) {
			isFileMoved = false;
		}

		return isFileMoved;
	}

	boolean copy(CopyInputStreamFactory inputStreamFactory, File target) {
		boolean sucessFullCopy;
		OutputStream outputStream = null;
		InputStream inputStream = null;

		if(inputStreamFactory == null && target == null) {
			return false;
		}

		try {
			target.getParentFile().mkdirs();
			inputStream = inputStreamFactory.create(COPY_RECEIVED_STREAM_TO_FILE);
			outputStream = ioServices.newFileOutputStream(target, COPY_RECEIVED_STREAM_TO_FILE);
			IOUtils.copy(inputStream, outputStream);
			sucessFullCopy = true;
		} catch (IOException e) {
			sucessFullCopy = false;
		} finally {
			ioServices.closeQuietly(inputStream);
			ioServices.closeQuietly(outputStream);
		}

		return sucessFullCopy;
	}

	public void register(FileSystemContentDaoExternalResourcesExtension extension) {
		externalResourcesExtensions.add(extension);
	}

	@Override
	public void initialize() {

	}

	@Override
	public void moveFileToVault(File file, String newContentId) {
        File target = getFileOf(newContentId);
		boolean isFileMovedInTheVault;
		boolean isFileReplicated = false;

		boolean isExecutedReplication = false;

        if (!target.exists()) {
			isFileMovedInTheVault = moveFile(file, target);

            if (!(replicatedRootFolder == null || target.equals(getReplicatedVaultFile(target)))) {
				isExecutedReplication = true;
            	if (!getReplicatedVaultFile(target).exists()) {
            		if(isFileMovedInTheVault) {
						isFileReplicated = fileCopy(target, getReplicatedVaultFile(target).getAbsolutePath());
					} else {
            			isFileReplicated = fileCopy(file, getReplicatedVaultFile(target).getAbsolutePath());
					}
                } else {
            		isFileReplicated = true;
				}
            }

            if(!isFileMovedInTheVault && !isFileReplicated && isExecutedReplication) {
            	throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToWriteVaultAndReplication(file);
			} else if(!isFileMovedInTheVault && !isExecutedReplication) {
				throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToWriteVault(file);
			} else if(!isFileMovedInTheVault) {
				addFileNotMovedInTheVault(newContentId);
			} else if(!isFileReplicated && isExecutedReplication) {
				addFileNotReplicated(newContentId);
			}
        }
	}

	public void addFileNotMovedInTheVault(String id) {
		try {
			Files.write(Paths.get(replicatedRootRecoveryFile.getPath()), (id + "\n").getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToSaveInformationInReplicationRecoveryFile(id);
		}
	}

	public void addFileNotReplicated(String id) {
		try {
			Files.write(Paths.get(vaultRecoveryFile.getPath()), (id + "\n").getBytes(), StandardOpenOption.APPEND);
		} catch (IOException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToSaveInformationInVaultRecoveryFile(id);
		}
	}

	public void readLogsAndRepairs(){
		repairVaultFiles();
		repairReplicationFiles();
	}

	private void repairVaultFiles() {
		BufferedReader b = null;

		if(replicatedRootRecoveryFile == null || !replicatedRootRecoveryFile.exists()){
			return;
		}


		try {
			b = ioServices.newBufferedFileReader(replicatedRootRecoveryFile, FILE_SYSTEM_CONTENT_DAO_FILE_STREAM_NAME);
			String lineRead;
			boolean didCopySucced;


			while((lineRead = b.readLine()) != null) {
				File file = getFileOf(lineRead);
				File replicatedVaultFile = getReplicatedVaultFile(file);
				if(!replicatedVaultFile.exists()) {
					// file got deleted.
					continue;
				}
				didCopySucced = fileCopy(replicatedVaultFile, file.getAbsolutePath());

				if(!didCopySucced) {
					throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_ErrorWhileCopingFileToTheReplicationVault(replicatedVaultFile);
				}
			}
		} catch (FileNotFoundException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FileNotFoundWhileRestauringVaultFiles(e);
		} catch (IOException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_IOErrorWhileRestauringVaultFiles(e);
		} finally {
			ioServices.closeQuietly(b);
		}

		if(!replicatedRootRecoveryFile.delete()) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_ErrorWhileDeletingReplicationRecoveryFile();
		}
		try {
			if(!replicatedRootRecoveryFile.createNewFile()) {
				throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_IOErrorWhileCreatingReplicationRecoveryFile();
			}
		} catch (IOException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_IOErrorWhileCreatingReplicationRecoveryFile(e);
		}
	}

	private void repairReplicationFiles() {
		BufferedReader b = null;
		try {
			b = ioServices.newBufferedFileReader(vaultRecoveryFile, FILE_SYSTEM_CONTENT_DAO_FILE_STREAM_NAME);
			String lineRead;
			boolean didCopySucced;
			while((lineRead = b.readLine()) != null) {
				File file = getFileOf(lineRead);

				if(!file.exists()) {
					// file got deleted.
					continue;
				}

				didCopySucced = fileCopy(file, getReplicatedVaultFile(file).getAbsolutePath());

				if(!didCopySucced) {
					throw new FileSystemContentDaoRuntimeException
							.FileSystemContentDaoRuntimeException_ErrorWhileCopingFileToTheVault(file.getAbsoluteFile());
				}
			}
		} catch (FileNotFoundException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FileNotFoundWhileRestauringReplicationVault(e);
		} catch (IOException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_IOErrorWhileRestauringReplicationVault(e);
		} finally {
			ioServices.closeQuietly(b);
		}

		if(!vaultRecoveryFile.delete()) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_ErrorWhileDeletingVaultRecoveryFile();
		}

		try {
			if(!vaultRecoveryFile.createNewFile()) {
				throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_IOErrorWhileCreatingVaultRecoveryFile();
			}
		} catch (IOException e) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_IOErrorWhileCreatingVaultRecoveryFile(e);
		}
	}

	public File getReplicatedVaultFile(File file) {
        return new File(file.getAbsolutePath().replace(rootFolder.getAbsolutePath(), replicatedRootFolder.getAbsolutePath()));
    }

	@Override
    public void add(String newContentId, InputStream newInputStream) {
		File target = getFileOf(newContentId);

		boolean vaultCopySucessful;
		boolean replicaCopySucessfull = false;
		boolean isReplicationExecuted = false;
		CopyInputStreamFactory inputStreamFactory = null;

		try {
			inputStreamFactory = ioServices.copyToReusableStreamFactory(newInputStream, COPY_RECEIVED_STREAM_TO_FILE);

			vaultCopySucessful = copy(inputStreamFactory, target);

			if (!(replicatedRootFolder == null || target.equals(getReplicatedVaultFile(target)))) {
				isReplicationExecuted = true;
				File replicatedTargetFile = getReplicatedVaultFile(target);

				replicaCopySucessfull = copy(inputStreamFactory, replicatedTargetFile);
			}
		}
		finally {
			ioServices.closeQuietly(inputStreamFactory);
		}

		if(!vaultCopySucessful && !replicaCopySucessfull && isReplicationExecuted) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToWriteVaultAndReplication(newContentId);
		} else if(!vaultCopySucessful && !isReplicationExecuted) {
			throw new FileSystemContentDaoRuntimeException.FileSystemContentDaoRuntimeException_FailedToWriteVault(newContentId);
		} else if(!vaultCopySucessful) {
			addFileNotMovedInTheVault(newContentId);
		} else if(!replicaCopySucessfull && isReplicationExecuted) {
			addFileNotReplicated(newContentId);
		}
    }


	@Override
	public void delete(List<String> contentIds) {
		for (String contentId : contentIds) {
			File file = getFileOf(contentId);
			file.delete();

            if (replicatedRootFolder != null) {
                getReplicatedVaultFile(file).delete();
            }
		}
	}

	@Override
	public InputStream getContentInputStream(String contentId, String streamName)
			throws ContentDaoException_NoSuchContent {

		if (contentId.startsWith("#")) {
			for (FileSystemContentDaoExternalResourcesExtension extension : externalResourcesExtensions) {
				if (contentId.startsWith("#" + extension.getId() + "=")) {
					String hash = StringUtils.substringAfter(contentId, "=");
					InputStream stream = extension.get(hash, streamName);
					if (stream == null) {
						throw new ContentDaoException_NoSuchContent(streamName);
					}
					return stream;
				}
			}
		}

		try {
			return new BufferedInputStream(ioServices.newFileInputStream(getFileOf(contentId), streamName));
		} catch (FileNotFoundException e1) {
			if (replicatedRootFolder != null) {
				try {
					return new BufferedInputStream(ioServices.newFileInputStream(getReplicatedVaultFile(getFileOf(contentId)), streamName));
				} catch (FileNotFoundException e2) {
					throw new ContentDaoException_NoSuchContent(contentId);
				}
			}

			throw new ContentDaoException_NoSuchContent(contentId);
		}
	}

	@Override
	public CloseableStreamFactory<InputStream> getContentInputStreamFactory(final String id)
			throws ContentDaoException_NoSuchContent {

		File file = getFileOf(id);

        try {
            return getContentInputStreamFactory(id, file);
        } catch (ContentDaoException_NoSuchContent e) {
            if (replicatedRootFolder != null) {
                return getContentInputStreamFactory(id, getReplicatedVaultFile(file));
            }

            throw e;
        }
	}

    public CloseableStreamFactory<InputStream> getContentInputStreamFactory(final String id, final File file)
            throws ContentDaoException_NoSuchContent {

        if (!file.exists()) {
            throw new ContentDaoException_NoSuchContent(id);
        }

        return new CloseableStreamFactory<InputStream>() {
            @Override
            public void close()
                    throws IOException {

            }

            @Override
            public long length() {
                return file.length();
            }

            @Override
            public InputStream create(String name)
                    throws IOException {
                try {
                    return new BufferedInputStream(ioServices.newFileInputStream(file, name));
                } catch (FileNotFoundException e) {
                    throw new ImpossibleRuntimeException(e);
                }
            }
        };
    }

	@Override
	public boolean isFolderExisting(String folderId) {
		File folder = new File(rootFolder, folderId.replace("/", File.separator));
		return folder.exists();
	}

	@Override
	public boolean isDocumentExisting(String documentId) {
        File file = getFileOf(documentId);

		boolean exists = file.exists();

        if (!(replicatedRootFolder == null || exists)) {
            exists = getReplicatedVaultFile(file).exists();
        }

        return exists;
	}

	@Override
	public List<String> getFolderContents(String folderId) {
		File folder = new File(rootFolder, folderId.replace("/", File.separator));
		String[] fileArray = folder.list();
		List<String> files = new ArrayList<>();

		if (fileArray != null) {
			for (String file : fileArray) {
				files.add(folderId + "/" + file);
			}
		}

		return files;
	}

    @Override
	public long getContentLength(String vaultContentId) {
		File file = getFileOf(vaultContentId);

		long length = file.length();

		if (replicatedRootFolder != null && length == 0) {
			length = getReplicatedVaultFile(file).length();
		}

		return length;
	}

	@Override
	public void moveFolder(String folderId, String newFolderId) {
		File folder = getFolder(folderId);
		if (!folder.exists()) {
			throw new ContentDaoRuntimeException_NoSuchFolder(folderId);
		}
		File newfolder = getFolder(newFolderId);
		newfolder.mkdirs();
		newfolder.delete();
		try {
			FileUtils.moveDirectory(folder, newfolder);
		} catch (IOException e) {
			throw new ContentDaoRuntimeException_CannotMoveFolderTo(folderId, newFolderId, e);
		}

	}

	public File getFolder(String folderId) {
		return new File(rootFolder, folderId.replace("/", File.separator));
	}

	@Override
	public void deleteFolder(String folderId) {

		File folder = getFolder(folderId);
		if (folder.exists()) {
			try {
				ioServices.deleteDirectory(getFolder(folderId));
			} catch (IOException e) {
				throw new ContentDaoRuntimeException_CannotDeleteFolder(folderId, e);
			}
		}
	}

	public File getFileOf(String contentId) {
		if (contentId.contains("/")) {
			return new File(rootFolder, contentId.replace("/", File.separator));

		} else {
			if (configuration.getContentDaoFileSystemDigitsSeparatorMode() == DigitSeparatorMode.THREE_LEVELS_OF_ONE_DIGITS) {
				StringBuilder name = new StringBuilder();

				String level1 = toCaseInsensitive(contentId.charAt(0));
				name.append(level1).append(File.separator);

				if (contentId.length() > 1) {
					String level2 = toCaseInsensitive(contentId.charAt(1));
					name.append(level1).append(level2).append(File.separator);

					if (contentId.length() > 2) {
						String level3 = toCaseInsensitive(contentId.charAt(2));
						name.append(level1).append(level2).append(level3).append(File.separator);
					}
				}

				name.append(toCaseInsensitive(contentId));
				return new File(rootFolder, name.toString());

			} else {
				String folderName = contentId.substring(0, 2);
				File folder = new File(rootFolder, folderName);
				return new File(folder, contentId);
			}
		}

	}

	private String toCaseInsensitive(char character) {
		String str = "" + character;
		return str;
	}

	private String toCaseInsensitive(String str) {
		StringBuilder stringBuilder = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			stringBuilder.append(toCaseInsensitive(str.charAt(i)));
		}
		return stringBuilder.toString();
	}

	@Override
	public void close() {

	}
}
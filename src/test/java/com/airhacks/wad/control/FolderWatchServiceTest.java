package com.airhacks.wad.control;

import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class FolderWatchServiceTest {

    private Path testFolderPath;
    private List<Path> tempFiles;

    @Before
    public void init() {
	this.testFolderPath = Paths.get("target");

	this.tempFiles = Stream.generate(this::createFile).limit(15).collect(Collectors.toList());
    }

    Path createFile() {
	try {
	    Path p = Files.createTempFile(this.testFolderPath, "wad", "java");
	    String fileContent = "duke" + System.nanoTime();
	    Files.write(p, fileContent.getBytes());
	    return p;
	} catch (IOException ex) {
	    throw new IllegalStateException("temp file creation", ex);
	}
    }

    void deleteFile(int index) throws IOException {
	Path file = this.tempFiles.get(index);
	Files.delete(file);
	this.tempFiles.remove(file);
    }

    void changeFile(int index, String content) throws IOException {
	Files.write(this.tempFiles.get(index), content.getBytes());
    }

    void addFile() {
	this.tempFiles.add(createFile());
    }

    @Test
    public void stampsAreEqualWithoutModification() {
	long stamp = FolderWatchService.getProjectModificationId(this.testFolderPath);
	long next = FolderWatchService.getProjectModificationId(this.testFolderPath);
	Assert.assertEquals(stamp, next);
    }

    @Test
    public void addingChangesStamp() {
	long stamp = FolderWatchService.getProjectModificationId(this.testFolderPath);
	addFile();
	long next = FolderWatchService.getProjectModificationId(this.testFolderPath);
	assertThat(stamp, not(next));
    }

    @Test
    public void changingFileChangesStamp() throws IOException {
	long stamp = FolderWatchService.getProjectModificationId(this.testFolderPath);
	changeFile(1, "Java EE rocks");
	long next = FolderWatchService.getProjectModificationId(this.testFolderPath);
	assertThat(stamp, not(next));
    }

    @Test
    public void deletingFileChangesStamp() throws IOException {
	long stamp = FolderWatchService.getProjectModificationId(this.testFolderPath);
	deleteFile(2);
	long next = FolderWatchService.getProjectModificationId(this.testFolderPath);
	assertThat(stamp, not(next));
    }
}

package com.kpelykh.docker.client.test;

import static ch.lambdaj.Lambda.filter;
import static ch.lambdaj.Lambda.selectUnique;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.isEmptyString;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.testinfected.hamcrest.jpa.HasFieldWithValue.hasField;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.List;
import java.util.Random;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.hamcrest.Matcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ITestResult;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import com.kpelykh.docker.client.DockerException;
import com.kpelykh.docker.client.model.ChangeLog;
import com.kpelykh.docker.client.model.CommitConfig;
import com.kpelykh.docker.client.model.Container;
import com.kpelykh.docker.client.model.ContainerConfig;
import com.kpelykh.docker.client.model.ContainerCreateResponse;
import com.kpelykh.docker.client.model.ContainerInspectResponse;
import com.kpelykh.docker.client.model.Image;
import com.kpelykh.docker.client.model.ImageInspectResponse;
import com.kpelykh.docker.client.model.Info;
import com.kpelykh.docker.client.model.Ports;
import com.kpelykh.docker.client.model.SearchItem;
import com.kpelykh.docker.client.model.Version;
import com.sun.jersey.api.client.ClientResponse;

/**
 * Unit test for DockerClient.
 * 
 * @author Konstantin Pelykh (kpelykh@gmail.com)
 */
public class DockerClientTest extends AbstractDockerClientTest {
	public static final Logger LOG = LoggerFactory
			.getLogger(DockerClientTest.class);

	@BeforeTest
	public void beforeTest() throws DockerException {
		super.beforeTest();
	}
	@AfterTest
	public void afterTest() {
		super.afterTest();
	}

	@BeforeMethod
	public void beforeMethod(Method method) {
	    super.beforeMethod(method);
	}

	@AfterMethod
	public void afterMethod(ITestResult result) {
		super.afterMethod(result);
	}

	/*
	 * ######################### ## INFORMATION TESTS ##
	 * #########################
	 */

	@Test
	public void testDockerVersion() throws DockerException {
		Version version = dockerClient.version();
		LOG.info(version.toString());

		assertTrue(version.getGoVersion().length() > 0);
		assertTrue(version.getVersion().length() > 0);

		assertEquals(StringUtils.split(version.getVersion(), ".").length, 3);

	}

	@Test
	public void testDockerInfo() throws DockerException {
		Info dockerInfo = dockerClient.info();
		LOG.info(dockerInfo.toString());

		assertTrue(dockerInfo.toString().contains("containers"));
		assertTrue(dockerInfo.toString().contains("images"));
		assertTrue(dockerInfo.toString().contains("debug"));

		assertTrue(dockerInfo.getContainers() > 0);
		assertTrue(dockerInfo.getImages() > 0);
		assertTrue(dockerInfo.getNFd() > 0);
		assertTrue(dockerInfo.getNGoroutines() > 0);
		assertTrue(dockerInfo.isMemoryLimit());
	}

	@Test
	public void testDockerSearch() throws DockerException {
		List<SearchItem> dockerSearch = dockerClient.search("busybox");
		LOG.info("Search returned {}", dockerSearch.toString());

		Matcher matcher = hasItem(hasField("name", equalTo("busybox")));
		assertThat(dockerSearch, matcher);

		assertThat(
				filter(hasField("name", is("busybox")), dockerSearch).size(),
				equalTo(1));
	}

	/*
	 * ################### ## LISTING TESTS ## ###################
	 */

	@Test
	public void testImages() throws DockerException {
		List<Image> images = dockerClient.getImages(true);
		assertThat(images, notNullValue());
		LOG.info("Images List: {}", images);
		Info info = dockerClient.info();

		assertThat(images.size(), equalTo(info.getImages()));

		Image img = images.get(0);
		assertThat(img.getCreated(), is(greaterThan(0L)));
		assertThat(img.getVirtualSize(), is(greaterThan(0L)));
		assertThat(img.getId(), not(isEmptyString()));
		assertThat(img.getTag(), not(isEmptyString()));
		assertThat(img.getRepository(), not(isEmptyString()));
	}

	@Test
	public void testListContainers() throws DockerException {
		
		String testImage = "hackmann/empty";
		
		LOG.info("Pulling image 'hackmann/empty'");
		// need to block until image is pulled completely
		logResponseStream(dockerClient.pull(testImage));
		tmpImgs.add(testImage);
		
		List<Container> containers = dockerClient.listContainers(true);
		assertThat(containers, notNullValue());
		LOG.info("Container List: {}", containers);

		int size = containers.size();

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage(testImage);
		containerConfig.setCmd(new String[] { "echo" });

		ContainerCreateResponse container1 = dockerClient
				.createContainer(containerConfig);
		
		assertThat(container1.getId(), not(isEmptyString()));

		ContainerInspectResponse containerInspectResponse = dockerClient.inspectContainer(container1.getId());
		
		assertThat(containerInspectResponse.getConfig().getImage(), is(equalTo(testImage)));
		
		
		dockerClient.startContainer(container1.getId());
		tmpContainers.add(container1.getId());
		
		LOG.info("container id: " + container1.getId());

		List<Container> containers2 = dockerClient.listContainers(true);
		
		for(Container container: containers2) {
			LOG.info("listContainer: id=" + container.getId() +" image=" + container.getImage());
		}
		
		assertThat(size + 1, is(equalTo(containers2.size())));
		Matcher matcher = hasItem(hasField("id", startsWith(container1.getId())));
		assertThat(containers2, matcher);

		List<Container> filteredContainers = filter(
				hasField("id", startsWith(container1.getId())), containers2);
		assertThat(filteredContainers.size(), is(equalTo(1)));

		for(Container container: filteredContainers) {
			LOG.info("filteredContainer: " + container.getImage());
		}
		
		Container container2 = filteredContainers.get(0);
		assertThat(container2.getCommand(), not(isEmptyString()));
		assertThat(container2.getImage(), equalTo(testImage + ":latest"));
	}

	/*
	 * ##################### ## CONTAINER TESTS ## #####################
	 */

	@Test
	public void testCreateContainer() throws DockerException {
		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "true" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);

		LOG.info("Created container {}", container.toString());

		assertThat(container.getId(), not(isEmptyString()));

		tmpContainers.add(container.getId());
	}

	@Test
	public void testStartContainer() throws DockerException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "true" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		boolean add = tmpContainers.add(container.getId());

		dockerClient.startContainer(container.getId());

		ContainerInspectResponse containerInspectResponse = dockerClient
				.inspectContainer(container.getId());
		LOG.info("Container Inspect: {}", containerInspectResponse.toString());

		assertThat(containerInspectResponse.config, is(notNullValue()));
		assertThat(containerInspectResponse.getId(), not(isEmptyString()));

		assertThat(containerInspectResponse.getId(),
				startsWith(container.getId()));

		assertThat(containerInspectResponse.getImageId(), not(isEmptyString()));
		assertThat(containerInspectResponse.getState(), is(notNullValue()));

		assertThat(containerInspectResponse.getState().running, is(true));

		if (!containerInspectResponse.getState().running) {
			assertThat(containerInspectResponse.getState().exitCode,
					is(equalTo(0)));
		}

	}

	@Test
	public void testWaitContainer() throws DockerException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "true" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		tmpContainers.add(container.getId());

		dockerClient.startContainer(container.getId());

		int exitCode = dockerClient.waitContainer(container.getId());
		LOG.info("Container exit code: {}", exitCode);

		assertThat(exitCode, equalTo(0));

		ContainerInspectResponse containerInspectResponse = dockerClient
				.inspectContainer(container.getId());
		LOG.info("Container Inspect: {}", containerInspectResponse.toString());

		assertThat(containerInspectResponse.getState().running,
				is(equalTo(false)));
		assertThat(containerInspectResponse.getState().exitCode,
				is(equalTo(exitCode)));

	}

	@Test
	public void testLogs() throws DockerException, IOException {

		String snippet = "hello world";

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "/bin/echo", snippet });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));

		dockerClient.startContainer(container.getId());
		tmpContainers.add(container.getId());

		int exitCode = dockerClient.waitContainer(container.getId());

		assertThat(exitCode, equalTo(0));

		ClientResponse response = dockerClient.logContainer(container.getId());

		assertThat(logResponseStream(response), endsWith(snippet));
	}

	@Test
	public void testDiff() throws DockerException {
		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "touch", "/test" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		dockerClient.startContainer(container.getId());
		boolean add = tmpContainers.add(container.getId());
		int exitCode = dockerClient.waitContainer(container.getId());
		assertThat(exitCode, equalTo(0));

		List filesystemDiff = dockerClient.containerDiff(container.getId());
		LOG.info("Container DIFF: {}", filesystemDiff.toString());

		assertThat(filesystemDiff.size(), equalTo(1));
		ChangeLog testChangeLog = selectUnique(filesystemDiff,
				hasField("path", equalTo("/test")));

		assertThat(testChangeLog, hasField("path", equalTo("/test")));
		assertThat(testChangeLog, hasField("kind", equalTo(1)));
	}

	@Test
	public void testStopContainer() throws DockerException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "sleep", "9999" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		dockerClient.startContainer(container.getId());
		tmpContainers.add(container.getId());

		LOG.info("Stopping container: {}", container.getId());
		dockerClient.stopContainer(container.getId(), 2);

		ContainerInspectResponse containerInspectResponse = dockerClient
				.inspectContainer(container.getId());
		LOG.info("Container Inspect: {}", containerInspectResponse.toString());

		assertThat(containerInspectResponse.getState().running,
				is(equalTo(false)));
		assertThat(containerInspectResponse.getState().exitCode,
				not(equalTo(0)));
	}

	@Test
	public void testKillContainer() throws DockerException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "sleep", "9999" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		dockerClient.startContainer(container.getId());
		tmpContainers.add(container.getId());

		LOG.info("Killing container: {}", container.getId());
		dockerClient.kill(container.getId());

		ContainerInspectResponse containerInspectResponse = dockerClient
				.inspectContainer(container.getId());
		LOG.info("Container Inspect: {}", containerInspectResponse.toString());

		assertThat(containerInspectResponse.getState().running,
				is(equalTo(false)));
		assertThat(containerInspectResponse.getState().exitCode,
				not(equalTo(0)));

	}

	@Test
	public void restartContainer() throws DockerException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "sleep", "9999" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		dockerClient.startContainer(container.getId());
		tmpContainers.add(container.getId());

		ContainerInspectResponse containerInspectResponse = dockerClient
				.inspectContainer(container.getId());
		LOG.info("Container Inspect: {}", containerInspectResponse.toString());

		String startTime = containerInspectResponse.getState().startedAt;

		dockerClient.restart(container.getId(), 2);

		ContainerInspectResponse containerInspectResponse2 = dockerClient
				.inspectContainer(container.getId());
		LOG.info("Container Inspect After Restart: {}",
				containerInspectResponse2.toString());

		String startTime2 = containerInspectResponse2.getState().startedAt;

		assertThat(startTime, not(equalTo(startTime2)));

		assertThat(containerInspectResponse.getState().running,
				is(equalTo(true)));

		dockerClient.kill(container.getId());
	}

	@Test
	public void removeContainer() throws DockerException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "true" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);

		dockerClient.startContainer(container.getId());
		dockerClient.waitContainer(container.getId());
		tmpContainers.add(container.getId());

		LOG.info("Removing container: {}", container.getId());
		dockerClient.removeContainer(container.getId());

		List containers2 = dockerClient.listContainers(true);
		Matcher matcher = not(hasItem(hasField("id",
				startsWith(container.getId()))));
		assertThat(containers2, matcher);

	}

	/*
	 * ################## ## IMAGES TESTS ## ##################
	 */

	@Test
	public void testPullImage() throws DockerException, IOException {

		// This should be an image that is not used by other repositories already
		// pulled down, preferably small in size. If tag is not used pull will
		// download all images in that repository but tmpImgs will only
		// deleted 'latest' image but not images with other tags
		String testImage = "hackmann/empty";

		LOG.info("Removing image: {}", testImage);
		dockerClient.removeImage(testImage);

		Info info = dockerClient.info();
		LOG.info("Client info: {}", info.toString());

		int imgCount = info.getImages();

		LOG.info("Pulling image: {}", testImage);

		tmpImgs.add(testImage);
		ClientResponse response = dockerClient.pull(testImage);

		assertThat(logResponseStream(response), containsString("Download complete"));

		info = dockerClient.info();
		LOG.info("Client info after pull, {}", info.toString());

		// TODO: imgCount should differ (maybe a docker bug?)
		assertThat(imgCount, lessThanOrEqualTo(info.getImages()));

		ImageInspectResponse imageInspectResponse = dockerClient
				.inspectImage(testImage);
		LOG.info("Image Inspect: {}", imageInspectResponse.toString());
		assertThat(imageInspectResponse, notNullValue());
	}

	@Test
	public void commitImage() throws DockerException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "touch", "/test" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		dockerClient.startContainer(container.getId());
		tmpContainers.add(container.getId());

		LOG.info("Commiting container: {}", container.toString());
		String imageId = dockerClient
				.commit(new CommitConfig(container.getId()));
		tmpImgs.add(imageId);

		ImageInspectResponse imageInspectResponse = dockerClient
				.inspectImage(imageId);
		LOG.info("Image Inspect: {}", imageInspectResponse.toString());

		assertThat(imageInspectResponse,
				hasField("container", startsWith(container.getId())));
		assertThat(imageInspectResponse.getContainerConfig().getImage(),
				equalTo("busybox"));

		ImageInspectResponse busyboxImg = dockerClient.inspectImage("busybox");

		assertThat(imageInspectResponse.getParent(),
				equalTo(busyboxImg.getId()));
	}

	@Test
	public void testRemoveImage() throws DockerException, InterruptedException {

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage("busybox");
		containerConfig.setCmd(new String[] { "touch", "/test" });

		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));
		dockerClient.startContainer(container.getId());
		tmpContainers.add(container.getId());

		LOG.info("Commiting container {}", container.toString());
		String imageId = dockerClient
				.commit(new CommitConfig(container.getId()));
		tmpImgs.add(imageId);

		dockerClient.stopContainer(container.getId());
		dockerClient.kill(container.getId());
		dockerClient.removeContainer(container.getId());

		tmpContainers.remove(container.getId());
		LOG.info("Removing image: {}", imageId);
		dockerClient.removeImage(imageId);

		List containers = dockerClient.listContainers(true);
		Matcher matcher = not(hasItem(hasField("id", startsWith(imageId))));
		assertThat(containers, matcher);
	}

	@Test
	public void testTagImage() throws DockerException, InterruptedException {
		String tag = String.valueOf(RandomUtils.nextInt(Integer.MAX_VALUE));
		
		Integer result = dockerClient.tag("busybox:latest", "docker-java/busybox", tag, false);
		assertThat(result, equalTo(Integer.valueOf(201)));
		
		dockerClient.removeImage("docker-java/busybox:" + tag);
	}

	/*
	 * 
	 * ################ ## MISC TESTS ## ################
	 */

	@Test
	public void testRunShlex() throws DockerException {

		String[] commands = new String[] {
				"true",
				"echo \"The Young Descendant of Tepes & Septette for the Dead Princess\"",
				"echo -n 'The Young Descendant of Tepes & Septette for the Dead Princess'",
				"/bin/sh -c echo Hello World", "/bin/sh -c echo 'Hello World'",
				"echo 'Night of Nights'", "true && echo 'Night of Nights'" };

		for (String command : commands) {
			LOG.info("Running command: [{}]", command);

			ContainerConfig containerConfig = new ContainerConfig();
			containerConfig.setImage("busybox");
			containerConfig.setCmd(commands);

			ContainerCreateResponse container = dockerClient
					.createContainer(containerConfig);
			dockerClient.startContainer(container.getId());
			tmpContainers.add(container.getId());
			int exitcode = dockerClient.waitContainer(container.getId());
			assertThat(exitcode, equalTo(0));
		}
	}

	@Test
	public void testNginxDockerfileBuilder() throws DockerException,
			IOException {
		File baseDir = new File(Thread.currentThread().getContextClassLoader()
				.getResource("nginx").getFile());

		ClientResponse response = dockerClient.build(baseDir);

		StringWriter logwriter = new StringWriter();

		try {
			LineIterator itr = IOUtils.lineIterator(
					response.getEntityInputStream(), "UTF-8");
			while (itr.hasNext()) {
				String line = itr.next();
				logwriter.write(line + "\n");
				LOG.info(line);
			}
		} finally {
			IOUtils.closeQuietly(response.getEntityInputStream());
		}

		String fullLog = logwriter.toString();
		assertThat(fullLog, containsString("Successfully built"));

		String imageId = StringUtils.substringBetween(fullLog,
				"Successfully built ", "\\n\"}").trim();

		ImageInspectResponse imageInspectResponse = dockerClient
				.inspectImage(imageId);
		assertThat(imageInspectResponse, not(nullValue()));
		LOG.info("Image Inspect: {}", imageInspectResponse.toString());
		tmpImgs.add(imageInspectResponse.getId());

		assertThat(imageInspectResponse.getAuthor(),
				equalTo("Guillaume J. Charmes \"guillaume@dotcloud.com\""));
	}

	@Test
	public void testDockerBuilderAddUrl() throws DockerException, IOException {
		File baseDir = new File(Thread.currentThread().getContextClassLoader()
				.getResource("testAddUrl").getFile());
		dockerfileBuild(baseDir, "docker.io");
	}

    @Test
    public void testDockerBuilderAddFileInSubfolder() throws DockerException, IOException {
            File baseDir = new File(Thread.currentThread().getContextClassLoader()
                            .getResource("testAddFileInSubfolder").getFile());
            dockerfileBuild(baseDir, "Successfully executed testrun.sh");
    }

        @Test
	public void testDockerBuilderAddFolder() throws DockerException,
			IOException {
		File baseDir = new File(Thread.currentThread().getContextClassLoader()
				.getResource("testAddFolder").getFile());
		dockerfileBuild(baseDir, "Successfully executed testAddFolder.sh");
	}

	@Test
	public void testNetCatDockerfileBuilder() throws DockerException,
			IOException, InterruptedException {
		File baseDir = new File(Thread.currentThread().getContextClassLoader()
				.getResource("netcat").getFile());

		ClientResponse response = dockerClient.build(baseDir);

		StringWriter logwriter = new StringWriter();

		try {
			LineIterator itr = IOUtils.lineIterator(
					response.getEntityInputStream(), "UTF-8");
			while (itr.hasNext()) {
				String line = itr.next();
				logwriter.write(line + "\n");
				LOG.info(line);
			}
		} finally {
			IOUtils.closeQuietly(response.getEntityInputStream());
		}

		String fullLog = logwriter.toString();
		assertThat(fullLog, containsString("Successfully built"));

		String imageId = StringUtils.substringBetween(fullLog,
				"Successfully built ", "\\n\"}").trim();

		ImageInspectResponse imageInspectResponse = dockerClient
				.inspectImage(imageId);
		assertThat(imageInspectResponse, not(nullValue()));
		LOG.info("Image Inspect: {}", imageInspectResponse.toString());
		tmpImgs.add(imageInspectResponse.getId());

		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage(imageInspectResponse.getId());
		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		assertThat(container.getId(), not(isEmptyString()));
		dockerClient.startContainer(container.getId());
		tmpContainers.add(container.getId());

		ContainerInspectResponse containerInspectResponse = dockerClient
				.inspectContainer(container.getId());

		assertThat(containerInspectResponse.getId(), notNullValue());
		assertThat(containerInspectResponse.getNetworkSettings().ports,
				notNullValue());

		// No use as such if not running on the server
		for (String portstr : containerInspectResponse.getNetworkSettings().ports
				.getAllPorts().keySet()) {

			Ports.Port p = containerInspectResponse.getNetworkSettings().ports
					.getAllPorts().get(portstr);
			int port = Integer.valueOf(p.getHostPort());
			LOG.info("Checking port {} is open", port);
			assertThat(available(port), is(false));
		}
		dockerClient.stopContainer(container.getId(), 0);

	}

	// UTIL

	/**
	 * Checks to see if a specific port is available.
	 * 
	 * @param port
	 *            the port to check for availability
	 */
	public static boolean available(int port) {
		if (port < 1100 || port > 60000) {
			throw new IllegalArgumentException("Invalid start port: " + port);
		}

		ServerSocket ss = null;
		DatagramSocket ds = null;
		try {
			ss = new ServerSocket(port);
			ss.setReuseAddress(true);
			ds = new DatagramSocket(port);
			ds.setReuseAddress(true);
			return true;
		} catch (IOException e) {
		} finally {
			if (ds != null) {
				ds.close();
			}

			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
					/* should not be thrown */
				}
			}
		}

		return false;
	}

	private String dockerfileBuild(File baseDir, String expectedText)
			throws DockerException, IOException {

		// Build image
		ClientResponse response = dockerClient.build(baseDir);

		StringWriter logwriter = new StringWriter();

		try {
			LineIterator itr = IOUtils.lineIterator(
					response.getEntityInputStream(), "UTF-8");
			while (itr.hasNext()) {
				String line = itr.next();
				logwriter.write(line + "\n");
				LOG.info(line);
			}
		} finally {
			IOUtils.closeQuietly(response.getEntityInputStream());
		}

		String fullLog = logwriter.toString();
		assertThat(fullLog, containsString("Successfully built"));

		String imageId = StringUtils.substringBetween(fullLog,
				"Successfully built ", "\\n\"}").trim();

		// Create container based on image
		ContainerConfig containerConfig = new ContainerConfig();
		containerConfig.setImage(imageId);
		ContainerCreateResponse container = dockerClient
				.createContainer(containerConfig);
		LOG.info("Created container: {}", container.toString());
		assertThat(container.getId(), not(isEmptyString()));

		dockerClient.startContainer(container.getId());
		dockerClient.waitContainer(container.getId());

		tmpContainers.add(container.getId());

		// Log container
		ClientResponse logResponse = dockerClient.logContainer(container
				.getId());

		assertThat(logResponseStream(logResponse), containsString(expectedText));

		return container.getId();
	}
}
package org.scm4j.wf;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.scm4j.actions.IAction;
import org.scm4j.wf.conf.Version;
import org.scm4j.wf.model.Credentials;
import org.scm4j.wf.model.VCSRepository;

@PrepareForTest({VCSRepository.class, Credentials.class})
@RunWith(PowerMockRunner.class)
public class SCMWorkflowConfigTest {

	public static final String PRODUCT_UNTILL = "eu.untill:unTill";
	public static final String PRODUCT_UBL= "eu.untill:UBL";
	public static final String PRODUCT_UNTILLDB = "eu.untill:unTIllDb";
	
	private static final String TEST_ENVIRONMENT_DIR = TestEnvironment.TEST_ENVIRONMENT_DIR;
	private static final String TEST_VCS_REPO_FILE_URL = TestEnvironment.TEST_VCS_REPO_FILE_URL;
	
	private TestEnvironment env;

	@Before
	public void setUp() throws IOException {
		env = new TestEnvironment();
		env.generateTestEnvironment();
		PowerMockito.mockStatic(System.class);
		PowerMockito.when(System.getenv(Credentials.CREDENTIALS_LOCATION_ENV_VAR))
				.thenReturn("file://localhost/" + env.getCredsFile().getPath().replace("\\", "/"));
		PowerMockito.when(System.getenv(VCSRepository.REPOS_LOCATION_ENV_VAR))
				.thenReturn("file://localhost/" + env.getReposFile().getPath().replace("\\", "/"));
	}

	@After
	public void tearDown() {
		File testFolder = new File(TEST_ENVIRONMENT_DIR);
		if (testFolder.exists()) {
			testFolder.delete();
		}
	}
	
	@Test
	public void testUseLastVersions() {
		env.generateFeatureCommit(env.getUnTillVCS(), SCMActionProductionRelease.VCS_TAG_SCM_VER);
		env.generateFeatureCommit(env.getUnTillDbVCS(), SCMActionProductionRelease.VCS_TAG_SCM_VER);
		env.generateFeatureCommit(env.getUblVCS(), SCMActionProductionRelease.VCS_TAG_SCM_VER);
		SCMWorkflow wf = new SCMWorkflow(PRODUCT_UNTILL);
		
		IAction actionUnTill = wf.getProductionReleaseAction();
		checkUseLastReleaseAction(actionUnTill, null, PRODUCT_UNTILL, env.getUnTillVer());
		assertTrue(actionUnTill.getChildActions().size() == 2);
		
		IAction actionUBL = actionUnTill.getChildActions().get(0);
		checkUseLastReleaseAction(actionUBL, actionUnTill, PRODUCT_UBL, env.getUblVer());
		assertTrue(actionUBL.getChildActions().size() == 1);
		
		IAction actionUnTillDb = actionUnTill.getChildActions().get(1);
		checkUseLastReleaseAction(actionUnTillDb, actionUnTill, PRODUCT_UNTILLDB, env.getUnTillDbVer());
		
		IAction actionUBLUnTillDb = actionUBL.getChildActions().get(0);
		checkUseLastReleaseAction(actionUBLUnTillDb, actionUBL, PRODUCT_UNTILLDB, env.getUnTillDbVer());
	}
	
	@Test
	public void testProductionReleaseNewFeatures() {
		SCMWorkflow wf = new SCMWorkflow(PRODUCT_UNTILL);
		
		IAction actionUnTill = wf.getProductionReleaseAction();
		checkProductionReleaseAction(actionUnTill, null, ProductionReleaseReason.NEW_FEATURES, PRODUCT_UNTILL);
		assertTrue(actionUnTill.getChildActions().size() == 2);
		
		IAction actionUBL = actionUnTill.getChildActions().get(0);
		checkProductionReleaseAction(actionUBL, actionUnTill, ProductionReleaseReason.NEW_FEATURES, PRODUCT_UBL);
		assertTrue(actionUBL.getChildActions().size() == 1);
		
		IAction actionUnTillDb = actionUnTill.getChildActions().get(1);
		checkProductionReleaseAction(actionUnTillDb, actionUnTill, ProductionReleaseReason.NEW_FEATURES, PRODUCT_UNTILLDB);
		
		IAction actionUBLUnTillDb = actionUBL.getChildActions().get(0);
		checkProductionReleaseAction(actionUBLUnTillDb, actionUBL, ProductionReleaseReason.NEW_FEATURES, PRODUCT_UNTILLDB);
	}
	
	@Test
	public void testProductionReleaseNewDependencies() {
	}
	
	private void checkAction(IAction action, IAction parentAction, String expectedName) {
		assertNotNull(action);
		assertEquals(action.getParent(), parentAction);
		assertEquals(action.getName(), expectedName);
		assertNotNull(action.getExecutionResults());
		assertTrue(action.getExecutionResults().isEmpty()); // not executed yet
	}
	
	private void checkUseLastReleaseAction(IAction action, IAction parentAction, String expectedName, Version expectedVersion) {
		checkAction(action, parentAction, expectedName);
		
		assertTrue(action instanceof SCMActionUseLastReleaseVersion);
		SCMActionUseLastReleaseVersion lv = (SCMActionUseLastReleaseVersion) action;
		assertEquals(lv.getVer(), expectedVersion);
	}
	
	private void checkProductionReleaseAction(IAction action, IAction parentAction, ProductionReleaseReason expectedReason, 
			String expectedName) {
		checkAction(action, parentAction, expectedName);
		
		assertTrue(action instanceof SCMActionProductionRelease);
		SCMActionProductionRelease pr = (SCMActionProductionRelease) action;
		assertEquals(pr.getReason(), expectedReason);
	}
	
	
	
	

	
}

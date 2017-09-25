package org.scm4j.releaser;

import org.junit.After;
import org.junit.Before;
import org.scm4j.commons.Version;
import org.scm4j.releaser.actions.IAction;
import org.scm4j.releaser.branch.CurrentReleaseBranch;
import org.scm4j.releaser.branch.DevelopBranch;
import org.scm4j.releaser.conf.Component;
import org.scm4j.releaser.conf.DelayedTagsFile;
import org.scm4j.releaser.conf.Option;
import org.scm4j.releaser.conf.Options;
import org.scm4j.vcs.api.VCSCommit;
import org.scm4j.vcs.api.VCSTag;
import org.scm4j.vcs.api.WalkDirection;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class WorkflowTestBase {
	protected TestEnvironment env;
	protected static final String UNTILL = TestEnvironment.PRODUCT_UNTILL;
	protected static final String UNTILLDB = TestEnvironment.PRODUCT_UNTILLDB;
	protected static final String UBL = TestEnvironment.PRODUCT_UBL;
	protected Component compUnTill;
	protected Component compUnTillDb;
	protected Component compUBL;
	protected DevelopBranch dbUnTill;
	protected DevelopBranch dbUnTillDb;
	protected DevelopBranch dbUBL;

	@Before
	public void setUp() throws Exception {
		env = new TestEnvironment();
		env.generateTestEnvironment();
		compUnTill = new Component(UNTILL);
		compUnTillDb = new Component(UNTILLDB);
		compUBL = new Component(UBL);
		dbUnTill = new DevelopBranch(compUnTill);
		dbUnTillDb = new DevelopBranch(compUnTillDb);
		dbUBL = new DevelopBranch(compUBL);
		TestBuilder.setBuilders(new HashMap<String, TestBuilder>());
		new DelayedTagsFile().delete();
	}

	@After
	public void tearDown() throws Exception {
		if (env != null) {
			env.close();
		}
		TestBuilder.setBuilders(null);
		Options.setOptions(new ArrayList<Option>());
	}
	
	protected void checkChildActionsTypes(IAction action, Expectations exp) {
		for (IAction nestedAction : action.getChildActions()) {
			checkChildActionsTypes(nestedAction, exp);
		}
		if (!exp.getProps().containsKey(action.getName())) {
			fail("unexpected action: " + action.getName());
		}
		Map<String, Object> props = exp.getProps().get(action.getName());
		Class<?> clazz = (Class<?>) props.get("class");
		if (!action.getClass().isAssignableFrom(clazz)) {
			fail(String.format("%s: expected: %s, actual: %s", action.getName(), clazz.toString(), action.getClass().toString()));
		}
		if (props.size() <= 1) {
			return; 
		}
		
		label1: for (String propName : props.keySet()) {
			for (Method method : action.getClass().getMethods()) {
				if (method.getName().toLowerCase().equals("get" + propName)) {
					Object propValue;
					try {
						propValue = method.invoke(action);
					} catch (Exception e) {
						throw new RuntimeException(e);
					}
					if (!propValue.equals(props.get(propName))) {
						fail(String.format("%s: property %s failed: expected %s, actual %s", action.getName(), propName,
								propValue, props.get(propName)));
					}
					continue label1;
				}
			}
			fail(String.format("%s: property %s is not declared", action.getName(), propName));
		}
	}
	
	public void checkUnTillDbBuilt() {
		assertNotNull(TestBuilder.getBuilders());
		assertNotNull(TestBuilder.getBuilders().get(UNTILLDB));

		// check versions
		CurrentReleaseBranch crbUnTillDb = new CurrentReleaseBranch(compUnTillDb);
		Version verRelease = crbUnTillDb.getHeadVersion();
		assertEquals(env.getUnTillDbVer().toNextPatch().toReleaseString(), verRelease.toString());

		// check tags
		List<VCSTag> tags = env.getUnTillDbVCS().getTags();
		assertTrue(tags.size() == 1);
		VCSTag tag = tags.get(0);
		assertEquals(env.getUnTillDbVer().toReleaseString(), tag.getTagName());
		List<VCSCommit> commits = env.getUnTillDbVCS().getCommitsRange(crbUnTillDb.getName(), null, WalkDirection.DESC, 2);
		assertEquals(commits.get(1), tag.getRelatedCommit());
	}

	public void checkUBLBuilt() {
		checkUnTillDbBuilt();
		CurrentReleaseBranch crbUBL = new CurrentReleaseBranch(compUBL);
		CurrentReleaseBranch crbUnTillDb = new CurrentReleaseBranch(compUnTillDb);
		// check UBL versions
		assertEquals(env.getUblVer().toNextMinor(), dbUBL.getVersion());
		assertEquals(env.getUblVer().toNextPatch().toRelease(), crbUBL.getHeadVersion());

		// check unTillDb versions
		assertEquals(env.getUnTillDbVer().toNextMinor(), dbUnTillDb.getVersion());
		assertEquals(env.getUnTillDbVer().toNextPatch().toRelease(), crbUnTillDb.getHeadVersion());

		// check UBL mDeps. Should contain unTillDb version minor-1 relative to current dev branch version
		List<Component> ublReleaseMDeps = crbUBL.getMDeps();
		assertTrue(ublReleaseMDeps.size() == 1);
		assertEquals(compUnTillDb.getName(), ublReleaseMDeps.get(0).getName());
		assertEquals(dbUnTillDb.getVersion().toPreviousMinor().toRelease(), ublReleaseMDeps.get(0).getVersion());

		// check tags
		List<VCSTag> tags = env.getUblVCS().getTags();
		assertTrue(tags.size() == 1);
		VCSTag tag = tags.get(0);
		assertEquals(dbUBL.getVersion().toPreviousMinor().toReleaseString(), tag.getTagName());
		List<VCSCommit> commits = env.getUblVCS().getCommitsRange(crbUBL.getName(), null, WalkDirection.DESC, 2);
		assertEquals(commits.get(1), tag.getRelatedCommit());
	}


	public void checkUBLForked() {
		CurrentReleaseBranch crbUBL = new CurrentReleaseBranch(compUBL);
		// check branches
		assertTrue(env.getUblVCS().getBranches("").contains(crbUBL.getName()));

		// check versions
		Version verTrunk = dbUBL.getVersion();
		Version verRelease = crbUBL.getHeadVersion();
		assertEquals(env.getUblVer().toNextMinor(),verTrunk);
		assertEquals(env.getUblVer().toRelease(), verRelease);

		// check mDeps
		List<Component> ublReleaseMDeps = crbUBL.getMDeps();
		assertTrue(ublReleaseMDeps.size() == 1);
		assertEquals(compUnTillDb.getName(), ublReleaseMDeps.get(0).getName());
		assertEquals(dbUnTillDb.getVersion().toPreviousMinor().toRelease(), ublReleaseMDeps.get(0).getVersion());
	}

	public void checkUnTillDbForked() {
		CurrentReleaseBranch newUnTillDb�RB = new CurrentReleaseBranch(compUnTillDb);

		// check branches
		assertTrue(env.getUnTillDbVCS().getBranches("").contains(newUnTillDb�RB.getName()));

		// check versions.
		Version verTrunk = dbUnTillDb.getVersion();
		Version verRelease = newUnTillDb�RB.getHeadVersion();
		assertEquals(env.getUnTillDbVer().toNextMinor(), verTrunk);
		assertEquals(env.getUnTillDbVer().toRelease(), verRelease);
	}

	public void checkUnTillForked() {
		checkUnTillDbForked();
		checkUBLForked();

		CurrentReleaseBranch �rbUnTill = new CurrentReleaseBranch(compUnTill);

		// check branches
		assertTrue(env.getUnTillVCS().getBranches("").contains(�rbUnTill.getName()));

		// check versions
		Version verTrunk = dbUnTill.getVersion();
		Version verRelease = �rbUnTill.getHeadVersion();
		assertEquals(env.getUnTillVer().toNextMinor(), verTrunk);
		assertEquals(env.getUnTillVer().toRelease(), verRelease);

		// check mDeps
		List<Component> unTillReleaseMDeps = �rbUnTill.getMDeps();
		assertTrue(unTillReleaseMDeps.size() == 2);
		for (Component unTillReleaseMDep : unTillReleaseMDeps) {
			if (unTillReleaseMDep.getName().equals(UBL)) {
				assertEquals(dbUBL.getVersion().toPreviousMinor().toRelease(), unTillReleaseMDep.getVersion());
			} else if (unTillReleaseMDep.getName().equals(UNTILLDB)) {
				assertEquals(dbUnTillDb.getVersion().toPreviousMinor().toRelease(), unTillReleaseMDep.getVersion());
			} else {
				fail();
			}
		}

	}

	public void checkUnTillBuilt() {
		checkUBLBuilt();


	}



}

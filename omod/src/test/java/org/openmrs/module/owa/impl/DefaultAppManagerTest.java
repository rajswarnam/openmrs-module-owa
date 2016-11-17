package org.openmrs.module.owa.impl;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyListOf;
import static org.mockito.Matchers.isNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.hamcrest.BaseMatcher;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Description;
import org.hamcrest.FeatureMatcher;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.openmrs.GlobalProperty;
import org.openmrs.api.AdministrationService;
import org.openmrs.module.owa.App;
import org.openmrs.module.owa.AppManager;
import org.openmrs.module.owa.OwaListener;
import org.openmrs.web.test.BaseModuleWebContextSensitiveTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.util.ReflectionUtils;

public class DefaultAppManagerTest extends BaseModuleWebContextSensitiveTest {
	
	DefaultAppManager appManager;
	
	@Autowired
	@Qualifier("adminService")
	AdministrationService administrationService;
	
	@Mock
	OwaListener listener;
	
	@Before
	public void setUp() throws Exception {
		initMocks(this);
		appManager = new DefaultAppManager();
		appManager.setOwaListeners(Arrays.asList(listener));
		administrationService.saveGlobalProperty(new GlobalProperty(AppManager.KEY_APP_FOLDER_PATH, "owa"));
	}
	
	@Test
	public void shouldNotifyListenerWhenInstallingApp() throws Exception {
		File file = new File("src/test/resources/designer.zip".replace("/", File.separator));
		appManager.installApp(file, "designer.zip", "http://localhost:8080");
		verify(listener).installedApp(any(App.class));
	}
	
	@Test
	public void shouldNotifyListenerWhenDeletingApp() throws Exception {
		String APP_NAME = "Polymer designer";
		File file = new File("src/test/resources/designer.zip".replace("/", File.separator));
		appManager.installApp(file, "designer.zip", "http://localhost:8080");
		App app = findApp(APP_NAME);
		Mockito.reset(listener);
		
		appManager.deleteApp(APP_NAME);
		verify(listener).deletedApp(app);
	}
	
	@Test
	public void shouldDeployToDeployedNameDirectory() throws Exception {
		//given
		File destinationDirectory = new File("owa/uicommons-customized");
		if (destinationDirectory.exists()) {
			FileUtils.forceDelete(destinationDirectory);
		}
		File file = new File("src/test/resources/refapp-uicommons-customized.zip".replace("/", File.separator));
		
		//when
		appManager.installApp(file, "refapp-uicommons-customized.zip", "http://localhost:8080");
		
		//then
		App deployedApp = findApp("Reference Application customized uicommons");
		assertThat(deployedApp, notNullValue());
		assertThat(deployedApp.getFolderName(), is(deployedApp.getDeployedName()));
		
		assertThat(destinationDirectory, exists());
		assertThat(new File(destinationDirectory, "manifest.webapp"), exists());
	}

	@Test
	public void shouldOverwriteAppWithSameDeployedName() throws Exception {
		File destinationDirectory = new File("owa/uicommons-customized");
		//given
		File refappCustomizedApp = new File("src/test/resources/refapp-uicommons-customized.zip".replace("/", File.separator));
		appManager.installApp(refappCustomizedApp, "refapp-uicommons-customized.zip", "http://localhost:8080");
		// file 'otherdistro.marker' is used to determine whether refapp-uicommons-customized app
		// had been replaced with otherdistro-uicommons-customized app
		assertThat(new File(destinationDirectory, "otherdistro.marker"), not(exists()));
		File otherCustomizedApp = new File("src/test/resources/otherdistro-uicommons-customized.zip".replace("/", File.separator));

		//when other app with deployed name 'uicommons-customized'
		appManager.installApp(otherCustomizedApp, "otherdistro-uicommons-customized.zip", "http://localhost:8080");

		//then check if marker file has been deployed
		assertThat(new File(destinationDirectory, "otherdistro.marker"), exists());
	}
	
	private App findApp(String appName) {
		for (App app : appManager.getApps()) {
			if (app.getName().equals(appName)) {
				return app;
			}
		}
		return null;
	}

	private Matcher<File> exists(){
		return new BaseMatcher<File>() {
			@Override
			public boolean matches(Object item) {
				final File file = (File) item;
				return file.exists();
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("file should exist");
			}

			@Override
			public void describeMismatch(Object item, Description description) {
				description.appendText("file does not exist");
			}
		};
	}
	
	@Test
	public void shouldNotifyListenerOnStartup() throws Exception {
		// Use reflection since AppManager.init() is private
		Method initMethod = ReflectionUtils.findMethod(DefaultAppManager.class, "init");
		ReflectionUtils.makeAccessible(initMethod);
		initMethod.invoke(appManager);
		
		verify(listener).appsReloaded(anyListOf(App.class));
	}
}

package com.redhat.kie.api.poc;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;
import org.kie.api.builder.KieModule;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

public class RunRulesWithKieJarTest extends AbstractKieJarTest {

    @Before
    public void setup() throws IOException {
        this.drlPath = "com.redhat.rules.one.file"; // build the rules with the declared object in one file
        Files.deleteIfExists( Paths.get( TARGET_DIR + File.separator + KJAR_NAME ) );
    }

    @Test
    public void runRulesWithGeneratedKieJar() throws Exception {
        buildKieModuleJar();
        KieSession session = getKieSessionFromJarFile();
        int fired = session.fireAllRules();
        session.dispose();
        assertEquals( 2, fired );
    }

    @Override
    protected KieSession getKieSessionFromJarFile() throws Exception {
        if ( !Files.exists( Paths.get( TARGET_DIR + File.separator + KJAR_NAME + ".jar" ) ) ) {
            fail( "KJar failed to build or was not built first" );
        }

        byte[] kJar = Files.readAllBytes( Paths.get( TARGET_DIR + File.separator + KJAR_NAME + ".jar" ) );
        Resource kJarResource = KIE_SERVICES.getResources().newByteArrayResource( kJar );
        KieModule kieModule = KIE_SERVICES.getRepository().addKieModule( kJarResource );
        KieContainer kContainer = KIE_SERVICES.newKieContainer( kieModule.getReleaseId(), Thread.currentThread().getContextClassLoader() );
        return kContainer.newKieSession( "ksession" );
    }
}
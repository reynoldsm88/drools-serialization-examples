package com.redhat.kie.api.poc;

import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Before;
import org.junit.Test;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import com.redhat.util.BinaryKModuleExternalizer;

public class RunRulesWithSerializedKJar extends AbstractKieJarTest {

    @Before
    public void setup() throws IOException {
        this.drlPath = "com.redhat.rules.one.file"; // build the rules with the declared object in one file
        Files.deleteIfExists( Paths.get( TARGET_DIR + File.separator + KJAR_NAME + ".jar" ) );
        Files.deleteIfExists( Paths.get( TARGET_DIR + File.separator + KJAR_NAME + ".bin" ) );
    }

    @Test( ) // this test will fail because of ClassNotFoundException
    public void shouldFailBecauseOfClassNotFoundException() throws Exception {
        buildKieModuleJar();
        KieSession session = getKieSessionFromJarFile();
        int fired = session.fireAllRules();
        session.dispose();
        assertEquals( 2, fired );
    }

    @Override
    protected KieSession getKieSessionFromJarFile() throws ClassNotFoundException {
        convertKJarToBin(); // first we serialize the jar into binary format using osamu's code
        KieContainer container = BinaryKModuleExternalizer.getKieContainer( new File( TARGET_DIR + File.separator + KJAR_NAME + ".bin" ) );
        return container.newKieSession( "ksession" );
    }

    private void convertKJarToBin() {
        File kjar = new File( TARGET_DIR + File.separator + KJAR_NAME + ".jar" );
        File binKjar = new File( TARGET_DIR + File.separator + KJAR_NAME + ".bin" );
        BinaryKModuleExternalizer.kjarToBinary( kjar, binKjar );
    }

}

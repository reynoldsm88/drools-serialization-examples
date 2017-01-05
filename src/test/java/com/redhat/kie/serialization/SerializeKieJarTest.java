package com.redhat.kie.serialization;

import static com.redhat.kie.serialization.util.Utils.createKieModule;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.kie.builder.impl.MemoryKieModule;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.builder.KieModule;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import com.redhat.kie.serialization.util.Utils;
import com.redhat.util.BinaryKModuleExternalizer;

/**
 * 
 * This test demonstrates the issues we've had with using KJars as a mechanism for serialization
 * 
 * As a reminder, the following three requirements apply 
 * 1) Rules must be built and serialized in a binary format that an be saved to a DB 
 * 2) Rules must be loaded from binary and not rebuilt at runtime 
 * 3) The stack size should not exceed the JVM defaults
 *
 */
public class SerializeKieJarTest {

    private static final String KJAR_NAME = "test-kjar";

    @Before
    public void setup() throws IOException {
        Files.deleteIfExists( Paths.get( Utils.TARGET_DIR + File.separator + KJAR_NAME + ".jar" ) );
        Files.deleteIfExists( Paths.get( Utils.TARGET_DIR + File.separator + KJAR_NAME + ".bin" ) );
    }

    /**
     * 
     * Generate a KJar and serialize it using the code from Osamu provided on sme-brms
     * 
     * This will fail because the declared fact type class is not found
     * 
     */
    @Test
    public void loadKjarFromBinaryResultsInClassNotFoundExceptionForDeclaredModel() throws Exception {
        try {
            List<Map<String,String>> resources = new ArrayList<Map<String,String>>();
            resources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "RulesAndDeclaredFact.drl" ); } } );
            createKJar( KJAR_NAME, createKieModule( resources ) );
            convertKjarToBin();
            
            KieContainer container = BinaryKModuleExternalizer.getKieContainer( new File( Utils.TARGET_DIR + File.separator + KJAR_NAME + ".bin" ) );
            KieSession session = container.newKieSession( "ksession" );
            
            session.fireAllRules();
            session.dispose();
        }
        catch ( RuntimeException re ) {
            System.err.println( "Runtime exception throw for error " + re.getCause() );
            assertTrue( re.getCause() instanceof ClassNotFoundException );
            assertTrue( re.getCause().getMessage().contains( "TransientFact" ) ); // Declared fact type is not found
        }
    }

    /**
     * 
     * Generating and loading a KJar will work, but the DRL is built from source.
     * Debugging it will lead you to the following call stack
     *
     *  this.getKieSessionFromJar
     *      KieContainerImpl.newKieSession
     *          KieContainerImpl.newKieSession
     *              KieContainerImpl.getKieBase
     *                  KieContainerImpl.createKieBase
     *                      AbstractKieModule.buildKnowledgePackages -- at this point you are compiling from DRL source
     * @formatter:on
     * @throws Exception
     */
    @Test
    public void generatedKieJarWorksBecauseRulesAreBuiltFromSource() throws Exception {
        List<Map<String,String>> resources = new ArrayList<Map<String,String>>();
        resources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "RulesAndDeclaredFact.drl" ); } } );
        createKJar( KJAR_NAME, createKieModule( resources ) );

        KieSession session = getKieSessionFromJar();
        int fired = session.fireAllRules();
        session.dispose();
        
        assertEquals( 2, fired );
    }

    public static void createKJar( String jarName, MemoryKieModule kmodule ) throws Exception {
        MemoryFileSystem trgMfs = kmodule.getMemoryFileSystem();
        trgMfs.writeAsJar( new File( Utils.TARGET_DIR ), "test-kjar" );
    }

    private KieSession getKieSessionFromJar() throws Exception {
        byte[] kJar = Files.readAllBytes( Paths.get( Utils.TARGET_DIR + File.separator + KJAR_NAME + ".jar" ) );
        Resource kJarResource = Utils.KIE_SERVICES.getResources().newByteArrayResource( kJar );
        KieModule kieModule = Utils.KIE_SERVICES.getRepository().addKieModule( kJarResource );
        KieContainer kContainer = Utils.KIE_SERVICES.newKieContainer( kieModule.getReleaseId(), Thread.currentThread().getContextClassLoader() );
        return kContainer.newKieSession( "ksession" );
    }

    private void convertKjarToBin() throws Exception {
        File kjar = new File( Utils.TARGET_DIR + File.separator + KJAR_NAME + ".jar" );
        File binKjar = new File( Utils.TARGET_DIR + File.separator + KJAR_NAME + ".bin" );
        BinaryKModuleExternalizer.kjarToBinary( kjar, binKjar );
    }
}
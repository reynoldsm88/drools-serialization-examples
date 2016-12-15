package com.redhat.kie.api.poc;

import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.kie.builder.impl.KieFileSystemImpl;
import org.drools.compiler.kie.builder.impl.KieModuleKieProject;
import org.drools.compiler.kie.builder.impl.MemoryKieModule;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.builder.Message;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.Message.Level;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel.KieSessionType;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.io.Resource;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.io.ResourceFactory;

public class TryUseKieMavenPluginTest {

    protected static final String SRC_MAIN_RESOURCES = System.getProperty( "user.dir" ) + File.separator + "src" + File.separator + "main" + File.separator + "resources";
    protected static final String TARGET_DIR = System.getProperty( "user.dir" ) + File.separator + "target";
    protected static final String KJAR_NAME = "test-kjar";
    protected static final KieServices KIE_SERVICES = KieServices.Factory.get();

    @Before
    public void setup() throws IOException {
        Files.deleteIfExists( Paths.get( TARGET_DIR + File.separator + KJAR_NAME + ".jar" ) );
    }

    @Test
    public void runRulesWithKieJarBuiltLikeKieMavenPlugin() throws Exception {
        createKieModuleJar();
        KieSession session = getKieSessionFromJarFile();
        int count = session.fireAllRules();
        System.out.println( count );
    }

    private void createKieModuleJar() throws Exception {
        Files.deleteIfExists( Paths.get( TARGET_DIR + File.separator + KJAR_NAME ) );
        KieModuleModel kproj = new KieModuleModelImpl();

        //@formatter:off
        KieBaseModel kieModule = kproj.newKieBaseModel( "kbase" )
            .setEqualsBehavior( EqualityBehaviorOption.EQUALITY )
            .setEventProcessingMode( EventProcessingOption.CLOUD )
            .addPackage( "com.redhat.rules" )
            .setDefault( true );

        kieModule.newKieSessionModel( "ksession" )
            .setType( KieSessionType.STATEFUL )
            .setClockType( ClockTypeOption.get( "realtime" ) )
            .setDefault( true );
        //@formatter:on

        KieFileSystemImpl kfs = (KieFileSystemImpl) KIE_SERVICES.newKieFileSystem();
        kfs.writeKModuleXML( ( (KieModuleModelImpl) kproj ).toXML() );

        ReleaseId releaseId = KIE_SERVICES.newReleaseId( "com.redhat.rules", "test-kjar", "0.0.1-SNAPSHOT" );
        kfs.generateAndWritePomXML( releaseId );

        KieBuilder kBuilder = KIE_SERVICES.newKieBuilder( kfs );
        buildAllRules( kfs, kBuilder );

        MemoryKieModule memoryKieModule = (MemoryKieModule) kBuilder.getKieModule();
        KieModuleKieProject k = new KieModuleKieProject( memoryKieModule );
        k.verify();
        MemoryFileSystem trgMfs = memoryKieModule.getMemoryFileSystem();
        trgMfs.writeAsJar( new File( TARGET_DIR ), KJAR_NAME );

    }

    private void buildAllRules( KieFileSystem kfs, KieBuilder kBuilder ) throws Exception {
        Files.list( Paths.get( SRC_MAIN_RESOURCES + File.separator + "com.redhat.rules.one.file" ) ).forEach( path -> {
            try {
                kfs.write( "src/main/resources/com.redhat.rules/" + path.getFileName(), ResourceFactory.newInputStreamResource( Files.newInputStream( path ) ) );
            }
            catch ( Exception e ) {
                fail( "Test failed because of " + e.getMessage() );
            }
        } );

        kBuilder.buildAll();
        if ( kBuilder.getResults().hasMessages( Level.ERROR ) ) {
            for ( Message m : kBuilder.getResults().getMessages( Level.ERROR ) ) {
                System.err.println( ( "KieBuilder error : " + m.toString() ) );
            }
            fail( "There was a problem building the kie module" );
        }
    }

    private KieSession getKieSessionFromJarFile() throws Exception {
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

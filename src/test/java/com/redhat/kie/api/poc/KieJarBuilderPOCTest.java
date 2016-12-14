package com.redhat.kie.api.poc;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.drools.compiler.compiler.io.memory.MemoryFileSystem;
import org.drools.compiler.kie.builder.impl.KieFileSystemImpl;
import org.drools.compiler.kie.builder.impl.MemoryKieModule;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
import org.junit.Ignore;
import org.junit.Test;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.builder.Message.Level;
import org.kie.api.builder.ReleaseId;
import org.kie.api.builder.model.KieBaseModel;
import org.kie.api.builder.model.KieModuleModel;
import org.kie.api.builder.model.KieSessionModel.KieSessionType;
import org.kie.api.conf.EqualityBehaviorOption;
import org.kie.api.conf.EventProcessingOption;
import org.kie.api.runtime.conf.ClockTypeOption;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class KieJarBuilderPOCTest {
    private static final Logger LOG = LoggerFactory.getLogger( KieJarBuilderPOCTest.class );
    private static final String DRL_DIR = System.getProperty( "user.dir" ) + File.separator + "src" + File.separator + "main" + File.separator + "resources" + File.separator+ "com.redhat.rules";
    private static final String TARGET_DIR = System.getProperty( "user.dir" ) + File.separator + "target";

    private KieServices ks = KieServices.Factory.get();

    @Test
    public void buildAllRulesKJarForLargeRulesetTest() throws Exception {
        Files.deleteIfExists( Paths.get( TARGET_DIR + "test-kjar" ) );
        long testStart = System.nanoTime();
        KieModuleModel kproj = new KieModuleModelImpl();

        KieBaseModel kieModule = kproj.newKieBaseModel( "drl-large-kbase" )
            .setEqualsBehavior( EqualityBehaviorOption.EQUALITY )
            .setEventProcessingMode( EventProcessingOption.CLOUD )
            .addPackage( "com.redhat.rules" )
            .setDefault( true );
          
        kieModule.newKieSessionModel( "drl-large-ksession" )
           .setType( KieSessionType.STATEFUL )
           .setClockType( ClockTypeOption.get( "realtime" ) )  
           .setDefault( true );
        KieFileSystemImpl kfs = (KieFileSystemImpl) ks.newKieFileSystem();
        kfs.writeKModuleXML( ( (KieModuleModelImpl) kproj ).toXML() );

        ReleaseId releaseId = ks.newReleaseId( "com.redhat.rules", "test-kjar", "0.0.1-SNAPSHOT" );
        kfs.generateAndWritePomXML( releaseId );

        KieBuilder kBuilder = ks.newKieBuilder( kfs );
        buildAllRules( kfs, kBuilder );

        MemoryKieModule memoryKieModule = (MemoryKieModule) kBuilder.getKieModule();
        MemoryFileSystem trgMfs = memoryKieModule.getMemoryFileSystem();
        trgMfs.writeAsJar( new File( TARGET_DIR ), "test-kjar" );
        assertTrue( Files.exists( Paths.get( TARGET_DIR ) ) );
        long testEnd = ( System.nanoTime() - testStart ) / 1000000;
        LOG.info( "Total test time : " + testEnd );
    }

    private void buildAllRules( KieFileSystem kfs, KieBuilder kBuilder ) throws Exception {
        Files.list( Paths.get( DRL_DIR ) ).forEach( path -> {
            try {
                LOG.info( "writing " + path.getFileName() + " to KieFileSystem" );
                kfs.write( "src/main/resources/com.redhat.rules/" + path.getFileName(), ResourceFactory.newInputStreamResource( Files.newInputStream( path ) ) );
            } catch ( Exception e ) {
                fail( "Test failed because of " + e.getMessage() );
            }
        } );

        LOG.info( "building all rules" );
        long buildStart = System.nanoTime();
        kBuilder.buildAll();
        if ( kBuilder.getResults().hasMessages( Level.ERROR ) ) {
            for ( Message m : kBuilder.getResults().getMessages( Level.ERROR ) ) {
                LOG.info( "KieBuilder error : " + m.toString() );
            }
            fail( "There was a problem building the kie module" );
        }
        long buildEnd = ( System.nanoTime() - buildStart ) / 1000000;
        LOG.info( "Build All Rules - Total time to build KieBase : " + buildEnd );
    }
}
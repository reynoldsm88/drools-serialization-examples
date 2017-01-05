package com.redhat.kie.serialization.util;

import static org.junit.Assert.fail;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import org.drools.compiler.kie.builder.impl.KieFileSystemImpl;
import org.drools.compiler.kie.builder.impl.MemoryKieModule;
import org.drools.compiler.kproject.models.KieModuleModelImpl;
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

public class Utils {

    public static final String SRC_MAIN_RESOURCES = System.getProperty( "user.dir" ) + File.separator + "src" + File.separator + "main" + File.separator + "resources";
    public static final String TARGET_DIR = System.getProperty( "user.dir" ) + File.separator + "target";
    public static final KieServices KIE_SERVICES = KieServices.Factory.get();
    public static final String RULES_FOLDER = "com.redhat.rules";

    public static MemoryKieModule createKieModule( List<Map<String, String>> resources ) throws Exception {
        KieModuleModel kproj = new KieModuleModelImpl();

        //@formatter:off
        KieBaseModel kieModule = kproj.newKieBaseModel( "kbase" )
            .setEqualsBehavior( EqualityBehaviorOption.EQUALITY )
            .setEventProcessingMode( EventProcessingOption.CLOUD )
            .setDefault( true );
        
        resources.forEach( resource -> {
            System.err.println( "adding package " + resource.get( "package" ) );
            kieModule.addPackage( resource.get( "package" ) );
         } );

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
        buildRules( kfs, kBuilder, resources );

        MemoryKieModule memoryKieModule = (MemoryKieModule) kBuilder.getKieModule();

        return memoryKieModule;
    }

    private static void buildRules( KieFileSystem kfs, KieBuilder kBuilder, List<Map<String, String>> resources ) throws Exception {

        resources.forEach( resource -> {
            try {
                Path p = Paths.get( SRC_MAIN_RESOURCES + File.separator + RULES_FOLDER + File.separator + resource.get( "filename" ) );
                System.err.println( "adding : " + p.toAbsolutePath().toString() );
                System.out.println( "adding to kfs as: " + "src/main/resources/" + resource.get( "package" ) + "/" + resource.get( "filename" ) );
                kfs.write( "src/main/resources/" + resource.get( "package" ) + "/" + resource.get( "filename" ), ResourceFactory.newInputStreamResource( Files.newInputStream( p ) ) );
            }
            catch ( Exception e ) {
                fail( e.getMessage() );
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
}
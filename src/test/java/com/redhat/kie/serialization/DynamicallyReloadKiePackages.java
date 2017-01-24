package com.redhat.kie.serialization;

import static com.redhat.kie.serialization.util.Utils.createKieModule;
import static com.redhat.kie.serialization.util.Utils.deserializeKieBase;
import static com.redhat.kie.serialization.util.Utils.deserializeKiePackages;
import static com.redhat.kie.serialization.util.Utils.serializeKieBase;
import static com.redhat.kie.serialization.util.Utils.serializeKiePackages;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.impl.KnowledgeBaseImpl;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.definition.KiePackage;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import com.redhat.kie.serialization.util.Utils;

@SuppressWarnings( "serial" )
public class DynamicallyReloadKiePackages {

    private static final String KBASE_BIN_FILE = Utils.TARGET_DIR + File.separator + "kbase.bin";
    private static final String KPACKAGE_BIN_FILE = Utils.TARGET_DIR + File.separator + "kpackage-modified.bin";

    @Before
    public void setup() throws Exception {
        Files.deleteIfExists( Paths.get( KBASE_BIN_FILE ) );
        Files.deleteIfExists( Paths.get( KPACKAGE_BIN_FILE ) );
    }

    /**
     *
     * In memory example of reloading kie packages that demonstrates it should work.
     *
     */
    @Test
    public void reloadKiePackageInAnInMemoryKieBase() throws Exception {
        // load the original rules
        List<Map<String, String>> originalResources = new ArrayList<Map<String, String>>();
        originalResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules.generated.facts" ); put( "filename", "DeclaredFact.drl" ); } } );
        originalResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "RulesOnly.drl" ); } } );

        KieContainer originalContainer = Utils.KIE_SERVICES.newKieContainer( createKieModule( originalResources ).getReleaseId(), Thread.currentThread().getContextClassLoader() );
        KieBase originalKbase = originalContainer.getKieBase();

        // make sure they work, expected fire count is 2 with the original rules
        KieSession originalSession = originalKbase.newKieSession();
        int originalCount = originalSession.fireAllRules();
        assertEquals( 2, originalCount );

        // load the modified rules
        List<Map<String, String>> modifiedResources = new ArrayList<Map<String, String>>();
        modifiedResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules.generated.facts" ); put( "filename", "DeclaredFact.drl" ); } } );
        modifiedResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "ModifiedRulesOnly.drl" ); } } );

        KieContainer modifiedContainer = Utils.KIE_SERVICES.newKieContainer( createKieModule( modifiedResources ).getReleaseId(), Thread.currentThread().getContextClassLoader() );
        KieBase modifiedKbase = modifiedContainer.getKieBase();

        // replace the packages in the original kbase with the modified ones
        reloadKiePackages( originalKbase, modifiedKbase.getKiePackages() );

        // using the original kie base with modified rules, the fire count should be 3
        KieSession modifiedSession = originalKbase.newKieSession();
        int modifiedCount = modifiedSession.fireAllRules();
        assertEquals( 3, modifiedCount );
    }

    /*
     * 
     * If the KieBase has not previously been used to createa a KieSession, rule package replacement will work
     * 
     */
    @Test
    public void reloadKiePackageInSerializedKieBaseWorksWithAnUnusedKieBase() throws Exception {
        KieSession session = null;

        // load the original rules
        List<Map<String,String>> originalResources = new ArrayList<Map<String,String>>();
        originalResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules.generated.facts" ); put( "filename", "DeclaredFact.drl" ); } } );
        originalResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "RulesOnly.drl" ); } } );

        // serialize the kie base and read it back in, we will keep a cached copy that will not be used to create a kie session.
        // dynamic rule package replacement will work with the untouched kbase
        serializeKieBase( KBASE_BIN_FILE, createKieModule( originalResources ) );
        KieBase serializedKieBase = deserializeKieBase( KBASE_BIN_FILE );  
        KieBase cachedSerializedKieBase = deserializeKieBase( KBASE_BIN_FILE );

        session = serializedKieBase.newKieSession();
        int originalRuleCount = session.fireAllRules();
        assertEquals( 2, originalRuleCount );

        // load the modified rules
        List<Map<String,String>> modifiedResources = new ArrayList<Map<String,String>>();
        modifiedResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules.generated.facts" ); put( "filename", "DeclaredFact.drl" ); } } );
        modifiedResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "ModifiedRulesOnly.drl" ); } } );

        // write the kie packages out and read them back in
        serializeKiePackages( KPACKAGE_BIN_FILE, createKieModule( modifiedResources ) );
        KnowledgeBaseImpl impl = (KnowledgeBaseImpl) serializedKieBase;
        Collection<KiePackage> modifiedPackages = deserializeKiePackages( KPACKAGE_BIN_FILE, impl.getConfig().getClassLoader() );

        // replace the rules in the kbase that we have not used to create a kie session
        reloadKiePackages( cachedSerializedKieBase, modifiedPackages );

        // everything will work fine
        session = cachedSerializedKieBase.newKieSession();
        int newCount = session.fireAllRules();
        assertEquals( 3, newCount );

    }

    /*
     * 
     * If the KieBase has been used to create a KieSession, reloading packages to it will not work
     *
     */
    @Test
    public void reloadKiePackagesInAPreviouslyUsedKieBase() throws Exception {
        try {
            // load the original rules
            List<Map<String,String>> originalResources = new ArrayList<Map<String,String>>();
            originalResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules.generated.facts" ); put( "filename", "DeclaredFact.drl" ); } } );
            originalResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "RulesOnly.drl" ); } } );

            // serialize the kie base and read it back in
            serializeKieBase( KBASE_BIN_FILE, createKieModule( originalResources ) );
            KieBase serializedKieBase = deserializeKieBase( KBASE_BIN_FILE );

            KieSession session = serializedKieBase.newKieSession(); // we don't even need to fire rules, it's just creating a ksession that causes the problem 

            // load the modified rules
            List<Map<String,String>> modifiedResources = new ArrayList<Map<String,String>>();
            modifiedResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules.generated.facts" ); put( "filename", "DeclaredFact.drl" ); } } );
            modifiedResources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "ModifiedRulesOnly.drl" ); } } );

            // write the kie packages out and read them back in
            serializeKiePackages( KPACKAGE_BIN_FILE, createKieModule( modifiedResources ) );
            KnowledgeBaseImpl impl = (KnowledgeBaseImpl) serializedKieBase;
            Collection<KiePackage> modifiedPackages = deserializeKiePackages( KPACKAGE_BIN_FILE, impl.getConfig().getClassLoader() );

            // this no longer works
            reloadKiePackages( serializedKieBase, modifiedPackages );
        }
        catch ( NullPointerException npe ) {
            System.err.println( "Problem reloading rule in serialized kiebase, this is due to the fact that a kie session was already created from it" );
            assertTrue( true );
        }

    }

    private void reloadKiePackages( KieBase kbase, Collection<KiePackage> modifiedPackages ) {
        KnowledgeBaseImpl impl = (KnowledgeBaseImpl) kbase;
        for ( KiePackage pkg : modifiedPackages ) {
            if ( pkg instanceof InternalKnowledgePackage ) {
                InternalKnowledgePackage p = (InternalKnowledgePackage) pkg;
                if ( impl.getPackage( pkg.getName() ) != null ) {
                    if ( !pkg.getName().contains( "generated" ) && !pkg.getName().contains( "model" ) ) { // the real implementation will also have to do similar filtering
                        System.out.println( "reloading rule package" + pkg.getName() );
                        impl.removeKiePackage( pkg.getName() );
                        impl.addPackage( p );
                    }
                }
            }
        }
    }
}

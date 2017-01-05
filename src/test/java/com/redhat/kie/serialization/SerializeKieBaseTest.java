package com.redhat.kie.serialization;

import static com.redhat.kie.serialization.util.Utils.createKieModule;
import static org.junit.Assert.assertEquals;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.compiler.kie.builder.impl.MemoryKieModule;
import org.drools.core.common.DroolsObjectInputStream;
import org.drools.core.common.DroolsObjectOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.KieBase;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

import com.redhat.kie.serialization.util.Utils;

/**
 * 
 * This test demonstrates that serializing from the KieBase works in the sense that it loads the binary rules without
 * building the DRL. This is the call stack for getting the KieSession... at no point does it call a builder method
 *  KieBaseImpl.newKieSession
 *      KieBaseImpl.newStatefulKnowledgeSession (line 294)
 *          KieBaseImpl.newStatefulKnowledgeSession (line 298)
 *              KnowledgeBaseImpl.newStatefulSession (line 1414)
 *                  KnowledgeBaseImpl.newStatefulSession( line 1430 )
 *                      WorkingMemoryFactory.createWorkingMemory
 * 
 * However, in a real environment with a deeply nested domain model and a very large rule set
 * this results in the stack size becoming huge. This is much harder to reproduce in a unit test.
 * 
 * As a reminder, the following three requirements apply 
 * 1) Rules must be built and serialized in a binary format that an be saved to a DB 
 * 2) Rules must be loaded from binary and not rebuilt at runtime 
 * 3) The stack size should not exceed the JVM defaults
 *
 */
public class SerializeKieBaseTest {

    private static final String BIN_FILE = "kbase.bin";

    @Before
    public void setUp() throws Exception {
        Files.deleteIfExists( Paths.get( Utils.TARGET_DIR + File.separator + BIN_FILE ) );
    }

    /**
     * This is just for debugging purpose to prove this works
     */
    @Test
    public void serializeKieBaseAsOneFile() throws Exception {
        List<Map<String,String>> resources = new ArrayList<Map<String,String>>();
        resources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "RulesAndDeclaredFact.drl" ); } } );
        
        serializeKieBase( createKieModule( resources ) );
        
        KieBase kbase = deserializeKieBase();
        KieSession session = kbase.newKieSession();
        int count = session.fireAllRules();
        assertEquals( 2, count );

    }

    /**
     * This is just for debugging purpose to prove this works
     */
    @Test
    public void serializeKieBaseAsTwoFiles() throws Exception {
        List<Map<String,String>> resources = new ArrayList<Map<String,String>>();
        resources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules.generated.facts" ); put( "filename", "DeclaredFact.drl" ); } } );
        resources.add( new HashMap<String, String>() { { put( "package", "com.redhat.rules" ); put( "filename", "RulesOnly.drl" ); } } );
        
        serializeKieBase( createKieModule( resources ) );
        
        KieBase kbase = deserializeKieBase();
        KieSession session = kbase.newKieSession();
        int count = session.fireAllRules();
        assertEquals( 2, count );

    }

    private void serializeKieBase( MemoryKieModule kmodule ) throws Exception {
        KieContainer container = Utils.KIE_SERVICES.newKieContainer( kmodule.getReleaseId(), Thread.currentThread().getContextClassLoader() );
        KieBase kbase = container.getKieBase();
        FileOutputStream fos = new FileOutputStream( new File( Utils.TARGET_DIR + File.separator + BIN_FILE ) );
        DroolsObjectOutputStream out = new DroolsObjectOutputStream( fos );
        out.writeObject( kbase );
        out.close();
    }

    private KieBase deserializeKieBase() throws Exception {
        KieBase kbase = null;
        try {
            FileInputStream fis = new FileInputStream( new File( Utils.TARGET_DIR + File.separator + BIN_FILE ) );
            DroolsObjectInputStream in = new DroolsObjectInputStream( fis );
            kbase = (KieBase) in.readObject();
            in.close();
            return kbase;
        }
        catch ( Exception e ) {
            System.err.println( "exception occurred " + e.getMessage() );
            System.err.println( "cause was " + e.getCause() );
            e.printStackTrace();
        }
        return kbase;

    }
}
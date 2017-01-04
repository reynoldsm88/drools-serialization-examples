package com.redhat.kie.serialization;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;

import org.drools.core.common.DroolsObjectInputStream;
import org.drools.core.common.DroolsObjectOutputStream;
import org.junit.Before;
import org.junit.Test;
import org.kie.api.io.ResourceType;
import org.kie.api.runtime.KieSession;
import org.kie.internal.KnowledgeBase;
import org.kie.internal.KnowledgeBaseFactory;
import org.kie.internal.builder.KnowledgeBuilder;
import org.kie.internal.builder.KnowledgeBuilderFactory;
import org.kie.internal.definition.KnowledgePackage;
import org.kie.internal.io.ResourceFactory;

import com.redhat.kie.serialization.util.Utils;

/**
 * 
 * This test demonstrates the issues we've had with serialzing KnowledgePackages via the legacy API 
 * According to the customer this used to work in Drools 5.x but does not in 6.x. The main issue is that
 * when serializing packages there is inconsistent behavior with declared fact types
 * 
 * As a reminder, the following three requirements apply:
 * 1) Rules must be built and serialized in a binary format that can be saved to a DB 
 * 2) Rules must be loaded from binary and not rebuilt at runtime 
 * 3) The stack size should not exceed the JVM defaults
 *
 */
@SuppressWarnings( "deprecation" )
public class SerializeKnowledgePackagesTest {

    private static final String BIN_FILE = "knowledge-packages.bin";

    @Before
    public void setup() throws IOException {
        Files.deleteIfExists( Paths.get( Utils.TARGET_DIR + File.separator + BIN_FILE ) );
    }

    /**
     * Serializing both the declared fact type and the rules in one file results in the
     * declared fact not being part of the kbase
     */
    @Test
    public void serializeFactTypeAndRulesInOneFile() throws Exception {
        serializePackagesAsBin( "RulesAndDeclaredFact.drl" );
        try {
            Collection<KnowledgePackage> packages = deserializeBinaryFile();
            KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
            kbase.addKnowledgePackages( packages );
        }
        catch ( RuntimeException e ) {
            // kbase does not have the declared fact type
            assertTrue( e.getCause() instanceof ClassNotFoundException );
            e.getMessage().contains( "TransientFact" );
        }
    }

    /**
     * Serializing just the declared fact results in the fact type being part of the kbase and now problems
     * creating a kie session
     */
    @Test
    public void serializeOnlyTheDeclaredFactType() throws Exception {
        serializePackagesAsBin( "DeclaredFact.drl" );
        Collection<KnowledgePackage> packages = deserializeBinaryFile();
        KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
        kbase.addKnowledgePackages( packages );
        KieSession session = kbase.newKieSession();
        assertTrue( kbase.getFactType( "com.redhat.rules", "TransientFact" ) != null ); // kbase has declared fact type
    }

    /**
     * Serializing the declared fact and the rules in separate files results in the declared fact type
     * not being part of the kbase
     */
    @Test
    public void serializeDeclaredFactAndRulesInSeparateFiles() throws Exception {
        serializePackagesAsBin( "DeclaredFact.drl", "RulesOnly.drl" );
        try {
            Collection<KnowledgePackage> packages = deserializeBinaryFile();
            KnowledgeBase kbase = KnowledgeBaseFactory.newKnowledgeBase();
            kbase.addKnowledgePackages( packages );
        }
        catch ( RuntimeException e ) {
            // kbase does not have the declared fact type
            assertTrue( e.getCause() instanceof ClassNotFoundException );
            e.getMessage().contains( "TransientFact" );
        }
    }

    @SuppressWarnings( "unchecked" )
    private Collection<KnowledgePackage> deserializeBinaryFile() throws Exception {
        FileInputStream fis = new FileInputStream( Paths.get( Utils.TARGET_DIR + File.separator + BIN_FILE ).toFile() );
        DroolsObjectInputStream in = new DroolsObjectInputStream( fis );
        Collection<KnowledgePackage> packages = (Collection<KnowledgePackage>) in.readObject();
        in.close();
        return packages;
    }

    private void serializePackagesAsBin( String... files ) {
        KnowledgeBuilder builder = KnowledgeBuilderFactory.newKnowledgeBuilder();

        Arrays.asList( files ).forEach( file -> {
            String filename = Utils.SRC_MAIN_RESOURCES + File.separator + Utils.RULES_FOLDER + File.separator + file;
            System.out.println( "adding file " + filename );
            builder.add( ResourceFactory.newFileResource( new File( filename ) ), ResourceType.DRL );
        } );
        try {

            FileOutputStream fos = new FileOutputStream( Utils.TARGET_DIR + File.separator + "knowledge-packages.bin" );
            DroolsObjectOutputStream out = new DroolsObjectOutputStream( fos );
            out.writeObject( builder.getKnowledgePackages() );
            out.close();
        }
        catch ( Exception e ) {
            fail( "failed to write KnowledgePackage bin file : " + e.getMessage() );
        }
    }
}
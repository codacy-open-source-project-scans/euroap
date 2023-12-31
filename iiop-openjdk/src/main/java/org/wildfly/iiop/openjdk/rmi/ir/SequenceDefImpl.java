/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.iiop.openjdk.rmi.ir;

import org.omg.CORBA.DefinitionKind;
import org.omg.CORBA.IDLType;
import org.omg.CORBA.IDLTypeHelper;
import org.omg.CORBA.IRObject;
import org.omg.CORBA.SequenceDef;
import org.omg.CORBA.SequenceDefOperations;
import org.omg.CORBA.SequenceDefPOATie;
import org.omg.CORBA.TypeCode;
import org.omg.CORBA.TypeCodePackage.BadKind;
import org.wildfly.iiop.openjdk.logging.IIOPLogger;

/**
 * SequenceDef IR object.
 *
 * @author <a href="mailto:osh@sparre.dk">Ole Husgaard</a>
 * @version $Revision: 81018 $
 */
class SequenceDefImpl
        extends IDLTypeImpl
        implements SequenceDefOperations {
    // Constants -----------------------------------------------------

    // Attributes ----------------------------------------------------

    // Static --------------------------------------------------------

    // Constructors --------------------------------------------------

    SequenceDefImpl(TypeCode typeCode, RepositoryImpl repository) {
        super(typeCode, DefinitionKind.dk_Sequence, repository);
    }

    // Public --------------------------------------------------------

    // LocalIRObject implementation ---------------------------------

    public IRObject getReference() {
        if (ref == null) {
            ref = org.omg.CORBA.SequenceDefHelper.narrow(
                    servantToReference(new SequenceDefPOATie(this)));
        }
        return ref;
    }

    public void allDone()
            throws IRConstructionException {
        // Get my element type definition: It should have been created now.
        try {
            element_type_def = IDLTypeImpl.getIDLType(type().content_type(),
                    repository);
        } catch (BadKind ex) {
            throw IIOPLogger.ROOT_LOGGER.badKindForTypeCode(type().kind().value());
        }

        getReference();
    }


    // SequenceDefOperations implementation --------------------------

    public int bound() {
        try {
            return type().length();
        } catch (BadKind ex) {
            // should never happen
            throw new RuntimeException();
        }
    }

    public void bound(int arg) {
        throw IIOPLogger.ROOT_LOGGER.cannotChangeRMIIIOPMapping();
    }

    public TypeCode element_type() {
        try {
            return type().content_type();
        } catch (BadKind ex) {
            // should never happen
            throw new RuntimeException();
        }
    }

    public IDLType element_type_def() {
        return IDLTypeHelper.narrow(element_type_def.getReference());
    }

    public void element_type_def(IDLType arg) {
        throw IIOPLogger.ROOT_LOGGER.cannotChangeRMIIIOPMapping();
    }


    // Package protected ---------------------------------------------

    // Private -------------------------------------------------------

    /**
     * My CORBA reference.
     */
    private SequenceDef ref = null;

    /**
     * My element type.
     */
    private LocalIDLType element_type_def;
}

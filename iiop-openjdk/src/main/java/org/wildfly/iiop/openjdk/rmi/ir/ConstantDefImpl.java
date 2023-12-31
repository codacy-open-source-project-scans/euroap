/*
 * Copyright The WildFly Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.wildfly.iiop.openjdk.rmi.ir;

import org.omg.CORBA.Any;
import org.omg.CORBA.ConstantDef;
import org.omg.CORBA.ConstantDefOperations;
import org.omg.CORBA.ConstantDefPOATie;
import org.omg.CORBA.ConstantDescription;
import org.omg.CORBA.ConstantDescriptionHelper;
import org.omg.CORBA.ContainedOperations;
import org.omg.CORBA.ContainedPackage.Description;
import org.omg.CORBA.DefinitionKind;
import org.omg.CORBA.IDLType;
import org.omg.CORBA.IDLTypeHelper;
import org.omg.CORBA.IRObject;
import org.omg.CORBA.TypeCode;
import org.wildfly.iiop.openjdk.logging.IIOPLogger;

/**
 * Constant IR object.
 *
 * @author <a href="mailto:osh@sparre.dk">Ole Husgaard</a>
 * @version $Revision: 81018 $
 */
public class ConstantDefImpl extends ContainedImpl implements ConstantDefOperations {

    ConstantDefImpl(String id, String name, String version,
                    TypeCode typeCode, Any value,
                    LocalContainer defined_in, RepositoryImpl repository) {
        super(id, name, version, defined_in,
                DefinitionKind.dk_Constant, repository);
        this.typeCode = typeCode;
        this.value = value;
    }


    public IRObject getReference() {
        if (ref == null) {
            ref = org.omg.CORBA.ConstantDefHelper.narrow(
                    servantToReference(new ConstantDefPOATie(this)));
        }
        return ref;
    }

    public void allDone()
            throws IRConstructionException {
        // Get my type definition: It should have been created now.
        type_def = IDLTypeImpl.getIDLType(typeCode, repository);
        getReference();
    }


    // ConstantDefOperations implementation ----------------------------

    public TypeCode type() {
        return typeCode;
    }

    public IDLType type_def() {
        return IDLTypeHelper.narrow(type_def.getReference());
    }

    public void type_def(IDLType arg) {
        throw IIOPLogger.ROOT_LOGGER.cannotChangeRMIIIOPMapping();
    }

    public Any value() {
        return value;
    }

    public void value(Any arg) {
        throw IIOPLogger.ROOT_LOGGER.cannotChangeRMIIIOPMapping();
    }


    // ContainedImpl implementation ----------------------------------

    public Description describe() {
        String defined_in_id = "IR";

        if (defined_in instanceof ContainedOperations)
            defined_in_id = ((ContainedOperations) defined_in).id();

        ConstantDescription d =
                new ConstantDescription(name, id, defined_in_id, version,
                        typeCode, value);

        Any any = getORB().create_any();

        ConstantDescriptionHelper.insert(any, d);

        return new Description(DefinitionKind.dk_Constant, any);
    }

    /**
     * My CORBA reference.
     */
    private ConstantDef ref = null;


    /**
     * My TypeCode.
     */
    private TypeCode typeCode;

    /**
     * My type definition.
     */
    private LocalIDLType type_def;

    /**
     * My value.
     */
    private Any value;

}

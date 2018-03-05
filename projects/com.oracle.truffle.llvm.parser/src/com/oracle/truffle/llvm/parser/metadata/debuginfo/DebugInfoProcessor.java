/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.parser.metadata.debuginfo;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.metadata.MDBaseNode;
import com.oracle.truffle.llvm.parser.metadata.MDNamedNode;
import com.oracle.truffle.llvm.parser.metadata.MetadataValueList;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.SymbolImpl;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceStaticMemberType;
import com.oracle.truffle.llvm.runtime.debug.LLVMSourceSymbol;
import com.oracle.truffle.llvm.runtime.debug.scope.LLVMSourceLocation;

import java.util.Map;

public final class DebugInfoProcessor {

    public static final SourceFunction DEFAULT_FUNCTION = new SourceFunction(LLVMSourceLocation.createUnavailable(LLVMSourceLocation.Kind.FUNCTION, "<unavailable>", "<unavailable>", 0, 0));

    private DebugInfoProcessor() {
    }

    public static DebugInfoFunctionProcessor processModule(ModelModule irModel, Source bitcodeSource, MetadataValueList metadata) {
        MDUpgrade.perform(metadata);

        final DebugInfoCache cache = new DebugInfoCache(metadata, irModel.getSourceStaticMembers());

        final Map<LLVMSourceSymbol, SymbolImpl> globals = irModel.getSourceGlobals();
        final Map<LLVMSourceStaticMemberType, SymbolImpl> staticMembers = irModel.getSourceStaticMembers();

        irModel.accept(new DebugInfoModelProcessor(cache, bitcodeSource, globals, staticMembers));

        final MDBaseNode cuNode = metadata.getNamedNode(MDNamedNode.COMPILEUNIT_NAME);
        final DebugInfoMetadataProcessor mdParser = new DebugInfoMetadataProcessor(cache, globals, staticMembers);
        if (cuNode != null) {
            cuNode.accept(mdParser);
        }

        return new DebugInfoFunctionProcessor(cache);

        // TODO metadata.localsAccept(mdParser);
    }
}

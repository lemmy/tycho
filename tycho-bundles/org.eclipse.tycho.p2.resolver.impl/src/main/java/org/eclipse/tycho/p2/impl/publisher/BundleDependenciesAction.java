/*******************************************************************************
 * Copyright (c) 2008, 2011 Sonatype Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Sonatype Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.tycho.p2.impl.publisher;

import java.io.File;
import java.util.ArrayList;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.equinox.p2.metadata.IRequirement;
import org.eclipse.equinox.p2.metadata.MetadataFactory;
import org.eclipse.equinox.p2.metadata.VersionRange;
import org.eclipse.equinox.p2.publisher.eclipse.BundlesAction;
import org.eclipse.equinox.spi.p2.publisher.PublisherHelper;
import org.eclipse.osgi.service.resolver.BundleDescription;
import org.eclipse.osgi.service.resolver.BundleSpecification;
import org.eclipse.osgi.service.resolver.ImportPackageSpecification;
import org.eclipse.osgi.util.ManifestElement;
import org.eclipse.tycho.p2.metadata.DependencyMetadataGenerator.OptionalResolutionAction;

@SuppressWarnings("restriction")
public class BundleDependenciesAction extends BundlesAction {

    /**
     * If true, treat optional Import-Package and Require-Bundle as required. If false, optional
     * Import-Package and Require-Bundle are ignored.
     */
    private final OptionalResolutionAction optionalAction;

    public BundleDependenciesAction(File[] locations, OptionalResolutionAction optionalAction) {
        super(locations);
        this.optionalAction = optionalAction;
    }

    @Override
    protected BundleDescription[] getBundleDescriptions(File[] bundleLocations, IProgressMonitor monitor) {
        /*
         * For reasons that I don't quite understand, p2 publisher BundlesAction generates two IUs
         * for org.eclipse.update.configurator bundle, the extra IU matching
         * org.eclipse.equinox.simpleconfigurator bundle. The extra IU results in wrong target
         * platform resolution for projects that depend on org.eclipse.equinox.simpleconfigurator
         * bundle or packages provided by it.
         * 
         * The solution is to suppress special handling of org.eclipse.update.configurator bundle
         * when generating p2 metadata of reactor projects and from what I can tell, this is
         * consistent with PDE behaviour (see
         * org.eclipse.pde.internal.build.publisher.GatherBundleAction ).
         */

        BundleDescription[] result = new BundleDescription[bundleLocations.length];
        for (int i = 0; i < bundleLocations.length; i++) {
            if (monitor.isCanceled())
                throw new OperationCanceledException();
            result[i] = createBundleDescription(bundleLocations[i]);
        }
        return result;
    }

    @Override
    protected void addImportPackageRequirement(ArrayList<IRequirement> reqsDeps, ImportPackageSpecification importSpec,
            ManifestElement[] rawImportPackageHeader) {
        VersionRange versionRange = PublisherHelper.fromOSGiVersionRange(importSpec.getVersionRange());
        final boolean required = !isOptional(importSpec) || optionalAction == OptionalResolutionAction.REQUIRE;
        if (required) {
            //TODO this needs to be refined to take into account all the attribute handled by imports
            reqsDeps.add(MetadataFactory.createRequirement(PublisherHelper.CAPABILITY_NS_JAVA_PACKAGE,
                    importSpec.getName(), versionRange, null, 1, 1, true /* greedy */));
        }
    }

    @Override
    protected void addRequireBundleRequirement(ArrayList<IRequirement> reqsDeps, BundleSpecification requiredBundle,
            ManifestElement[] rawRequireBundleHeader) {
        VersionRange versionRange = PublisherHelper.fromOSGiVersionRange(requiredBundle.getVersionRange());
        final boolean required = !requiredBundle.isOptional() || optionalAction == OptionalResolutionAction.REQUIRE;
        if (required) {
            reqsDeps.add(MetadataFactory.createRequirement(CAPABILITY_NS_OSGI_BUNDLE, requiredBundle.getName(),
                    versionRange, null, 1, 1, true /* greedy */));
        }
    }
}

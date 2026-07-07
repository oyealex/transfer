package testsupport;

import org.springframework.core.type.classreading.MetadataReader;
import org.springframework.core.type.classreading.MetadataReaderFactory;
import org.springframework.core.type.filter.TypeFilter;

import java.io.IOException;
import java.util.Set;

public class DuplicateClassNameExcludeFilter implements TypeFilter {

    private static final Set<String> EXCLUDED_FQNS = Set.of(
            "com.ecommerce.user.config.SecurityConfig",
            "com.ecommerce.review.service.ReviewApprovedEventListener",
            "com.ecommerce.app.config.SecurityConfigTest$PublicTestController",
            "com.ecommerce.app.config.SecurityConfigTest$UserTestController",
            "com.ecommerce.app.config.SecurityConfigTest$AdminTestController"
    );

    @Override
    public boolean match(MetadataReader metadataReader,
                         MetadataReaderFactory metadataReaderFactory) throws IOException {
        String className = metadataReader.getClassMetadata().getClassName();
        return EXCLUDED_FQNS.contains(className);
    }
}

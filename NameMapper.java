import org.opensaml.saml2.core.Assertion;
import org.opensaml.saml2.core.Attribute;
import org.opensaml.xml.XMLObject;
import java.util.List;
import java.util.Map;

public class MyAssertionMapper implements AssertionMapper {

    @Override
    public void mapAssertion(Assertion assertion, Map<String, Object> localSubjectAttributes) {
        // Extract attributes from the SAML assertion and map them to local subject attributes
        
        // Loop through assertion attributes
        for (Attribute attribute : assertion.getAttributeStatements().get(0).getAttributes()) {
            // Get attribute name
            String attributeName = attribute.getName();
            
            // Get attribute values
            List<XMLObject> attributeValues = attribute.getAttributeValues();
            
            // Extract attribute values as strings
            StringBuilder attributeValueStringBuilder = new StringBuilder();
            for (XMLObject attributeValue : attributeValues) {
                attributeValueStringBuilder.append(attributeValue.getDOM().getTextContent()).append(",");
            }
            String attributeValueString = attributeValueStringBuilder.toString();
            
            // Store attribute name and values in local subject attributes map
            localSubjectAttributes.put(attributeName, attributeValueString);
        }
    }
}

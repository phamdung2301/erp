# pack_project.py
import json
import os

# List of all files we created or modified for the commercial e-Rescue system
FILES_TO_PACKAGE = [
    "src/main/java/web/rescue/erp/entity/RescueRequest.java",
    "src/main/java/web/rescue/erp/service/RescueRequestService.java",
    "src/main/java/web/rescue/erp/entity/ChatMessage.java",
    "src/main/java/web/rescue/erp/repository/ChatMessageRepository.java",
    "src/main/java/web/rescue/erp/api/ChatApiController.java",
    "src/main/java/web/rescue/erp/websocket/RescueWebSocketHandler.java",
    "src/main/java/web/rescue/erp/entity/RescuerInventory.java",
    "src/main/java/web/rescue/erp/repository/RescuerInventoryRepository.java",
    "src/main/java/web/rescue/erp/api/RescuerApiController.java",
    "src/main/java/web/rescue/erp/api/CustomerApiController.java",
    "src/main/java/web/rescue/erp/controller/RescuerController.java",
    "src/main/resources/templates/customer/dashboard.html",
    "src/main/resources/templates/rescuer/dashboard.html",
    "src/main/resources/static/js/customer.js",
    "src/main/resources/static/js/rescuer.js",
    "src/test/java/web/rescue/erp/service/RescueRequestServiceTest.java",
    "README.md"
]

def main():
    print("[INFO] Scanning customized files for packaging...")
    bundle = {}
    
    for filepath in FILES_TO_PACKAGE:
        if os.path.exists(filepath):
            print(f"[PACK] Packaging: {filepath}")
            with open(filepath, "r", encoding="utf-8") as f:
                bundle[filepath] = f.read()
        else:
            print(f"[WARN] File not found: {filepath}")
            
    # Generate the rebuild_project.py content
    rebuild_script_content = f'''# rebuild_project.py
# Automatic Restore Bootstrap for e-Rescue Customized Platform
# Designed by Antigravity Senior AI Coding Assistant

import os
import json

# Encoded files data map
FILES_DATA = {json.dumps(bundle, indent=4, ensure_ascii=False)}

def main():
    print("[INFO] Starting Automatic Restore of e-Rescue Customized Platform...")
    print("-----------------------------------------------------------------")
    
    restored_count = 0
    for filepath, content in FILES_DATA.items():
        # Ensure directories exist
        dirname = os.path.dirname(filepath)
        if dirname and not os.path.exists(dirname):
            os.makedirs(dirname, exist_ok=True)
            
        print(f"[RESTORE] Restoring: {{filepath}}")
        with open(filepath, "w", encoding="utf-8") as f:
            f.write(content)
        restored_count += 1
        
    print("-----------------------------------------------------------------")
    print(f"[SUCCESS] {{restored_count}} custom files have been perfectly restored!")
    print("Run 'mvn test' to verify the build integrity.")

if __name__ == "__main__":
    main()
'''
    
    # Write the rebuild_project.py
    with open("rebuild_project.py", "w", encoding="utf-8") as f:
        f.write(rebuild_script_content)
        
    print("---------------------------------------------------------")
    print("[SUCCESS] Generated 'rebuild_project.py' successfully!")
    print("Keep this script. Run it in any clean Spring Boot workspace folder to restore 100% of your changes.")

if __name__ == "__main__":
    main()

echo "Renaming native folders to prod names"
for native_folder in natives/*/; do
    # Get the base name of the native folder
    original_name=$(basename "$native_folder")
    native_folder_name="$original_name"

    # Rename the native folder based on the suffix
    if [[ "$native_folder_name" == *"-ON" ]]; then
        native_folder_name="${native_folder_name/-ON/-vulkan}"
    elif [[ "$native_folder_name" == *"-OFF" ]]; then
        native_folder_name="${native_folder_name/-OFF/}"
    else
        echo "Not renaming $original_name"
        continue
    fi

    mv "natives/${original_name}" "natives/${native_folder_name}"
done

echo "Flooding resources with default CPU natives"
for resource_folder in src/main/resources/*/; do
    # Get the base folder name (instead of full path)
    folder_name=$(basename "$resource_folder")

    # Find the matching built natives folder
    for native_folder in natives/*/; do
        # Only copy if this native folder EXACTLY matches (meaning Vulkan natives don't get passed, only CPU)
        if [[ $(basename "$native_folder") == "$folder_name" ]]; then
            echo "Copying from $native_folder to $resource_folder"
            # Copy contents from the renamed native folder into the resource folder
            cp -r "$native_folder"* "$resource_folder"
        fi
    done
done

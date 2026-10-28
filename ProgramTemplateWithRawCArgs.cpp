#include "FrankoRuntime.hpp"

__FRANKO_PROGRAM__

int main(int argc, char** argv)
{
    Franko_Dynamic_Array<Franko_Dynamic_Array<uint8_t>> frankoArgs;

    if (argc > 0) {
        frankoArgs.init(static_cast<uint32_t>(argc));

        for (int i = 0; i < argc; ++i) {
            const uint32_t idx = static_cast<uint32_t>(i);
            const char* raw = (argv[i] != nullptr) ? argv[i] : "";
            const size_t len = std::strlen(raw);

            if (len > 0) {
                frankoArgs.data[idx].init(static_cast<uint32_t>(len));
                std::memcpy(frankoArgs.data[idx].data, raw, len);
            }
        }
    }

    return FUNC_main_HASH_db73581e(&frankoArgs);
}

// @@common
#include "FrankoRuntime.hpp"
static int32_t mathx_bias()
{
    return 0;
}
// @@end

// @@mathx::add
namespace NS_mathx {
int32_t FUNC_add_da8ad4dd(int32_t PARAM_a, int32_t PARAM_b)
{
    return PARAM_a + PARAM_b + mathx_bias();
}
}
// @@end

// @@mathx::scale
namespace NS_mathx {
int32_t FUNC_scale_da8ad4dd(int32_t PARAM_value, int32_t PARAM_factor)
{
    return PARAM_value * PARAM_factor + mathx_bias();
}
}
// @@end

// @@mathx::bump
namespace NS_mathx {
int32_t FUNC_bump_a0a001d7(Franko_Thin_Pointer<int32_t> PARAM_value)
{
    if (PARAM_value.data == nullptr) {
        return -1;
    }
    *PARAM_value.data = *PARAM_value.data + 1;
    return *PARAM_value.data;
}
}
// @@end

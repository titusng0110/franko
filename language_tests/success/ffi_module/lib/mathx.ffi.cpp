// @@common
#include "FrankoRuntime.hpp"
static int32_t mathx_bias()
{
    return 0;
}
// @@end

// @@mathx::add
namespace NS_mathx {
int32_t FUNC_add_da8ad4dd(int32_t VAR_a, int32_t VAR_b)
{
    return VAR_a + VAR_b + mathx_bias();
}
}
// @@end

// @@mathx::scale
namespace NS_mathx {
int32_t FUNC_scale_da8ad4dd(int32_t VAR_value, int32_t VAR_factor)
{
    return VAR_value * VAR_factor + mathx_bias();
}
}
// @@end

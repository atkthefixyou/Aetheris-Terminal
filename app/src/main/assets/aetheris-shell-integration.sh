# Aetheris Terminal shell integration (OSC 133 marks + cwd/branch). Safe to source repeatedly.
case $- in *i*) ;; *) return 0 2>/dev/null || exit 0 ;; esac
[ -n "$AETHERIS_SHELL_INTEGRATION" ] && return 0
AETHERIS_SHELL_INTEGRATION=1
_aeth_info() {
  printf '\033]6973;%s;%s\007' "$PWD" "$(git symbolic-ref --short -q HEAD 2>/dev/null)"
}
if [ -n "$BASH_VERSION" ]; then
  _aeth_precmd() { local _s=$?; printf '\033]133;D;%s\007' "$_s"; _aeth_info; return $_s; }
  PROMPT_COMMAND="_aeth_precmd${PROMPT_COMMAND:+;$PROMPT_COMMAND}"
  PS0=$'\033]133;C\007'
elif [ -n "$ZSH_VERSION" ]; then
  _aeth_precmd() { local _s=$?; printf '\033]133;D;%s\007' "$_s"; _aeth_info; return $_s }
  _aeth_preexec() { printf '\033]133;C\007' }
  precmd_functions=(_aeth_precmd $precmd_functions)
  preexec_functions=(_aeth_preexec $preexec_functions)
fi

# Aetheris Terminal shell integration. Source at the END of ~/.zshrc / ~/.bashrc.
# Marks (OSC 133), cwd/branch (OSC 6973); empty prompt and no echo: typing happens in the INPUT card.
# Set AETHERIS_PLAIN=1 before sourcing to keep the normal prompt/echo.
case $- in *i*) ;; *) return 0 2>/dev/null || exit 0 ;; esac
[ -n "$AETHERIS_SHELL_INTEGRATION" ] && return 0
AETHERIS_SHELL_INTEGRATION=1
# Xoa loi chao cu (motd) khi vao phien.
printf '\033[2J\033[3J\033[H'
_aeth_info() {
  printf '\033]6973;%s;%s\007' "$PWD" "$(git symbolic-ref --short -q HEAD 2>/dev/null)"
}
if [ -n "$BASH_VERSION" ]; then
  _aeth_status() { _AETH_S=$?; }
  _aeth_precmd() {
    printf '\033]133;D;%s\007' "$_AETH_S"; _aeth_info
    if [ -z "$AETHERIS_PLAIN" ]; then PS1=''; PS2=''; stty -echo 2>/dev/null; fi
    return $_AETH_S
  }
  PROMPT_COMMAND="_aeth_status;${PROMPT_COMMAND:+$PROMPT_COMMAND;}_aeth_precmd"
  PS0=$'\033]133;C\007'
  if [ -z "$AETHERIS_PLAIN" ]; then PS1=''; PS2=''; stty -echo 2>/dev/null; fi
elif [ -n "$ZSH_VERSION" ]; then
  _aeth_status() { _AETH_S=$?; }
  _aeth_precmd() {
    printf '\033]133;D;%s\007' "$_AETH_S"; _aeth_info
    if [ -z "$AETHERIS_PLAIN" ]; then PROMPT=''; RPROMPT=''; PS1=''; stty -echo 2>/dev/null; fi
    return $_AETH_S
  }
  _aeth_preexec() { printf '\033]133;C\007'; }
  precmd_functions=(_aeth_status $precmd_functions _aeth_precmd)
  preexec_functions=(_aeth_preexec $preexec_functions)
  if [ -z "$AETHERIS_PLAIN" ]; then PROMPT=''; RPROMPT=''; unsetopt zle 2>/dev/null; stty -echo 2>/dev/null; fi
fi

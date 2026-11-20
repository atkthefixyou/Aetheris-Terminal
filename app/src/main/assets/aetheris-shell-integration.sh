# Aetheris Terminal shell integration. Source at the END of ~/.zshrc / ~/.bashrc.
# Marks (OSC 133), cwd/branch (OSC 6973); empty prompt and no echo: typing happens in the INPUT card.
# Set AETHERIS_PLAIN=1 before sourcing to keep the normal prompt/echo.
case $- in *i*) ;; *) return 0 2>/dev/null || exit 0 ;; esac
[ -n "$AETHERIS_SHELL_INTEGRATION" ] && return 0
AETHERIS_SHELL_INTEGRATION=1
_AETH_H=$HISTCMD
# Xoa loi chao cu (motd) khi vao phien.
printf '\033[2J\033[3J\033[H'
# Dau hieu lenh chay xong (sau OSC 133;D nen khong lot vao output duoc copy).
_aeth_done() {
  # Dau hieu hoan tat do app in mot lan sau ca khoi lenh (AetherisPanel); khong in o day.
  return 0
}
_aeth_info() {
  printf '\033]6973;%s;%s\007' "$PWD" "$(git symbolic-ref --short -q HEAD 2>/dev/null)"
}
if [ -n "$BASH_VERSION" ]; then
  _aeth_status() { _AETH_S=$?; }
  _aeth_precmd() {
    printf '\033]133;D;%s\007' "$_AETH_S"
    if [ "$HISTCMD" != "$_AETH_H" ]; then _aeth_done "$_AETH_S"; fi
    _AETH_H=$HISTCMD; _aeth_info
    if [ -z "$AETHERIS_PLAIN" ]; then PS1=''; PS2=''; stty -echo 2>/dev/null; fi
    return $_AETH_S
  }
  PROMPT_COMMAND="_aeth_status;${PROMPT_COMMAND:+$PROMPT_COMMAND;}_aeth_precmd"
  PS0=$'\033]133;C\007'
  # Bat echo khi lenh chay de thay cau tra loi go vao; precmd tat lai.
  [ -z "$AETHERIS_PLAIN" ] && PS0="$PS0"'$(stty echo 2>/dev/null)'
  if [ -z "$AETHERIS_PLAIN" ]; then PS1=''; PS2=''; stty -echo 2>/dev/null; fi
elif [ -n "$ZSH_VERSION" ]; then
  _aeth_status() { _AETH_S=$?; }
  _aeth_precmd() {
    printf '\033]133;D;%s\007' "$_AETH_S"
    if [ -n "$_AETH_RAN" ]; then _aeth_done "$_AETH_S"; _AETH_RAN=; fi
    _aeth_info
    if [ -z "$AETHERIS_PLAIN" ]; then PROMPT=''; RPROMPT=''; PS1=''; stty -echo 2>/dev/null; fi
    return $_AETH_S
  }
  _aeth_preexec() { _AETH_RAN=1; printf '\033]133;C\007'; [ -z "$AETHERIS_PLAIN" ] && stty echo 2>/dev/null; }
  precmd_functions=(_aeth_status $precmd_functions _aeth_precmd)
  preexec_functions=(_aeth_preexec $preexec_functions)
  if [ -z "$AETHERIS_PLAIN" ]; then PROMPT=''; RPROMPT=''; unsetopt zle 2>/dev/null; stty -echo 2>/dev/null; fi
fi

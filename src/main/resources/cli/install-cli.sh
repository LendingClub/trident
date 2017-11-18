#!/usr/env/bin bash

DOT_HOME=${HOME}/.trident
DOT_BIN=${DOT_HOME}/bin
CONFIG_FILE=${DOT_HOME}/config
TRIDENT_EXE=${DOT_BIN}/trident

mkdir -p ${DOT_HOME}
mkdir -p ${DOT_BIN}

TRIDENT_URL="TRIDENT_TEMPLATE_URL"

if [ ! -f "${CONFIG_FILE}" ]; then

cat <<EOF >${CONFIG_FILE}
{
  "url" : "${TRIDENT_URL}",
  "username" : "${USER}"
}
EOF

fi




echo "Downloading trident CLI and installing it in: ${TRIDENT_EXE}"
curl -so ${TRIDENT_EXE} ${TRIDENT_URL}/cli/download
chmod +x ${TRIDENT_EXE}


if [[ -n "$ZSH_VERSION" ]]; then
    zsh_shell=true
else
    bash_shell=true
fi

# Update PATH for  appropriate shell
if [[ "$zsh_shell" == 'true' ]]; then
    grep -q '.trident/bin' ${HOME}/.zshenv
    if [ $? -ne 0 ]; then
        echo
        echo Adding ${HOME}/.trident/bin to your PATH in .zshenv.
        echo
        echo You will need to run: 
        echo
        echo . ${HOME}/.zshenv
        echo
        echo  or start a new shell
        echo
        echo "export PATH=\$PATH:$HOME/.trident/bin" >>${HOME}/.zshenv
    fi
else
    grep -q '.trident/bin' ${HOME}/.bash_profile
    if [ $? -ne 0 ]; then
        echo
        echo Adding ${HOME}/.trident/bin to your PATH in .bash_profile.
        echo
        echo You will need to run: 
        echo
        echo . ${HOME}/.bash_profile
        echo
        echo  or start a new shell
        echo
        echo "export PATH=\$PATH:$HOME/.trident/bin" >>${HOME}/.bash_profile
    fi
fi


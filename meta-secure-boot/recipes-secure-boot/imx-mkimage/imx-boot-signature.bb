LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/files/common-licenses/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit cst hab deploy features_check

REQUIRED_MACHINE_FEATURES = "imx-boot-signature"

DEPENDS += "\
    nxp-cst-signer-native \
    imx-boot \
    bc-native \
    util-linux-native \
"

SRC_URI = "file://mx8_create_fuse_commands.sh"

# For signing the imx-boot image after it has been deployed to DEPLOY_DIR_IMAGE
do_compile[depends] += "imx-boot:do_deploy"

BOOT_IMAGE_SD = "imx-boot-${MACHINE}-sd.bin-${SIGNED_TARGET}"
BOOT_TOOLS = "imx-boot-tools"
BOOT_NAME = "imx-boot"

CST_SRK_FUSE ?= "${CST_PATH}/crts/SRK_1_2_3_4_fuse.bin"

# from imx-boot_1.0.bb
SOC_FAMILY                  = "INVALID"
SOC_FAMILY:mx8-generic-bsp  = "mx8"
SOC_FAMILY:mx8m-generic-bsp = "mx8m"
SOC_FAMILY:mx8x-generic-bsp = "mx8x"

# Signs the imx-boot image. This command assumes that the PKI tree was generated.
do_sign_boot_image() {
    bbnote "Signing boot image"

    SIGNDIR="${S}"
}

do_sign_boot_image:append:ahab() {

    # Creating a cfg file for cst_signer
    if [ -e "${CST_PATH}/csf_ahab.cfg" ]; then
        # Use user defined keys
        install -m 0755 ${CST_PATH}/csf_ahab.cfg ${SIGNDIR}/csf.cfg
    else
        # Use default keys
        install -m 0755 ${DEPLOY_DIR_IMAGE}/${BOOT_TOOLS}/csf_ahab.cfg.sample ${SIGNDIR}/csf.cfg
    fi
}

do_sign_boot_image:append:hab4() {

    # Creating a cfg file for cst_signer
    if [ -e "${CST_PATH}/csf_hab4.cfg" ]; then
        # Use user defined keys
        install -m 0755 ${CST_PATH}/csf_hab4.cfg ${SIGNDIR}/csf.cfg
    else
        # Use default keys
        install -m 0755 ${DEPLOY_DIR_IMAGE}/${BOOT_TOOLS}/csf_hab4.cfg.sample ${SIGNDIR}/csf.cfg
    fi
}

do_sign_boot_image:append() {

    # Check if SD image is available
    if [ ! -e "${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_SD}" ]; then
        bbfatal 'imx-boot SD image is not available to sign'
    fi
    # Generate signed image using cst_signer
    CST_PATH=${CST_PATH} ${DEPLOY_DIR_IMAGE}/${BOOT_TOOLS}/cst_signer -d -i ${DEPLOY_DIR_IMAGE}/${BOOT_IMAGE_SD} -c ${SIGNDIR}/csf.cfg
    if [ ! -e "${S}/signed-${BOOT_IMAGE_SD}" ]; then
        bbfatal 'Image signing failed'
    fi
}

do_generate_fuse_cmds() {
    bbnote "Generating fuse cmds for u-boot"
    # Generate file with instructions for programming fuses, only mx8* for now
    if [ "${SOC_FAMILY}" = "mx8" ] || [ "${SOC_FAMILY}" = "mx8x" ] || [ "${SOC_FAMILY}" = "mx8m" ]; then
        ${WORKDIR}/mx8_create_fuse_commands.sh ${SOC_FAMILY} ${CST_SRK_FUSE} "${WORKDIR}/$(basename ${CST_SRK_FUSE}).u-boot-cmds"
    fi
}

do_compile() {
    do_sign_boot_image
    do_generate_fuse_cmds
}

do_deploy() {
    # Copy signed image to DEPLOYDIR and link it to boot image
    if [ -e "${S}/signed-${BOOT_IMAGE_SD}" ]; then
        install -m 0644 ${S}/signed-${BOOT_IMAGE_SD} ${DEPLOY_DIR_IMAGE}/
        ln -sf ${DEPLOY_DIR_IMAGE}/signed-${BOOT_IMAGE_SD} ${DEPLOY_DIR_IMAGE}/${BOOT_NAME}
    else
        bbfatal "ERROR: Could not deploy Signed image"
    fi

    # Deploy U-Boot Fuse Commands, if they have been generated
    UBOOT_CMDS=${WORKDIR}/$(basename ${CST_SRK_FUSE}).u-boot-cmds
    if [ -e "${UBOOT_CMDS}" ]; then
        install -m 0644 ${UBOOT_CMDS} ${DEPLOY_DIR_IMAGE}/
    fi
}

addtask do_deploy after do_compile

PACKAGE_ARCH = "${MACHINE_ARCH}"

COMPATIBLE_MACHINE = "(mx8-generic-bsp|mx9-generic-bsp)"

EXCLUDE_FROM_WORLD = "1"

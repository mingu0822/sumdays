function buildProfilePayload(user) {
    return {
        mode: user.profile_mode,
        imageUrl: user.profile_image_url ?? null,
        faceId: user.avatar_face_id ?? null,
        eyesId: user.avatar_eyes_id ?? null,
        mouthId: user.avatar_mouth_id ?? null,
        accessoryId: user.avatar_accessory_id ?? null
    };
}

module.exports = { buildProfilePayload };
const bcrypt = require('bcrypt');
const jwt = require('jsonwebtoken');
const util = require('util');
const upload = require('../middlewares/uploadMiddleware');
const uploadPromise = util.promisify(upload.single('profileImage'));
const path = require('path');
const fs = require('fs').promises;

// =================================================================
// ★★★ 중요: 이 비밀 키와 DB 정보는 절대로 코드에 하드코딩하면 안 됩니다. ★★★
// 실제 프로덕션 환경에서는 .env 파일과 같은 환경 변수로 안전하게 관리해야 합니다.
// =================================================================
const JWT_SECRET = 'Sumdays_Project_Super_Secret_Key_!@#$%^&*()';

const { pool } = require('../db/db');

exports.login = async (req, res) => {
    const { email, password } = req.body;

    console.log(`[로그인 시도] 이메일: ${email}`);

    // 입력 값 유효성 검사
    if (!email || !password) {
        return res.status(400).json({
            success: false,
            message: '이메일과 비밀번호를 모두 입력해주세요.'
        });
    }
    
    try {
        const sql = 'SELECT id, email, password_hash, nickname FROM users WHERE email = ?';
        const [rows] = await pool.query(sql, [email]);
        const user = rows[0]; 

        // no exist 
        if (!user) {
            // 사용자가 존재하지 않는 경우
            console.log(`[로그인 실패] 존재하지 않는 이메일: ${email}`);
            // 보안을 위해 "이메일이 없음" 대신 "정보가 일치하지 않음"으로 응답합니다.
            return res.status(401).json({
                success: false,
                message: '이메일 또는 비밀번호가 올바르지 않습니다.'
            });
        }

        // 2. 사용자가 입력한 비밀번호와 DB에 저장된 해시된 비밀번호 비교
        const isPasswordMatch = await bcrypt.compare(password, user.password_hash);

        if (isPasswordMatch) {
            // 비밀번호 일치 (로그인 성공)
            console.log(`[로그인 성공] 사용자 ID: ${user.id}`);

            // JWT 페이로드(Payload) 생성
            const payload = {
                userId: user.id,
                email: user.email
            };

            // JWT 생성 (유효 기간: 7일)
            const token = jwt.sign(payload, JWT_SECRET, { expiresIn: '7d' });

            // 클라이언트에게 성공 응답 전송
            res.status(200).json({
                success: true,
                message: '로그인 성공',
                userId: user.id,
                token: token,
                nickname : user.nickname
            });

        } else {
            // 비밀번호 불일치
            console.log(`[로그인 실패] 비밀번호 불일치: ${email}, ${password}, ${user.password_hash}`);
            return res.status(401).json({
                success: false,
                message: '이메일 또는 비밀번호가 올바르지 않습니다.'
            });
        }

    } catch (error) {
        console.error('[서버 오류] 로그인 처리 중 에러 발생:', error);
        res.status(500).json({
            success: false,
            message: '서버 내부 오류가 발생했습니다.'
        });
    }
};

// ★★★ POST /api/signup: 회원가입 요청을 처리하는 엔드포인트 추가 ★★★
exports.signup = async (req, res) => {
    const { nickname, email, password } = req.body;
    console.log(`[회원가입 시도] 이메일: ${email}`);
    const connection = await pool.getConnection(); // 트랜잭션을 위한 커넥션 확보

    try {
        await connection.beginTransaction(); // 트랜잭션 시작

        // 1. 이메일 중복 확인
        const [existing] = await connection.query('SELECT id FROM users WHERE email = ?', [email]);
        if (existing.length > 0) {
            connection.release();
            return res.status(409).json({ success: false, message: '이미 사용 중인 이메일입니다.' });
        }

        // 2. 비밀번호 해싱
        const hashedPassword = await bcrypt.hash(password, 10);

        // 3. users 테이블 삽입
        const [userResult] = await connection.query(
            'INSERT INTO users (nickname, email, password_hash) VALUES (?, ?, ?)',
            [nickname, email, hashedPassword]
        );
        const newUserId = userResult.insertId;

        // 4. user_info 테이블 초기화 
        await connection.query(
            'INSERT INTO user_info (user_id) VALUES (?)',
            [newUserId]
        );

        await connection.commit(); // 모든 작업 확정
        console.log(`[회원가입 성공] ID: ${newUserId}, 이메일: ${email}`);
        res.status(201).json({ success: true, message: '회원가입 성공!' });

    } catch (error) {
        await connection.rollback(); // 에러 발생 시 롤백
        console.error('[서버 오류] 회원가입 중 롤백됨:', error);
        res.status(500).json({ success: false, message: '서버 내부 오류' });
    } finally {
        connection.release(); // 커넥션 반납
    }
};

exports.changePassword = async (req, res) => {
    const userId = req.user.userId;
    const { currentPassword, newPassword } = req.body;

    console.log(`[비밀번호 변경 시도] userId=${userId}`);

    // 1. 요청 값 확인
    if (!currentPassword || !newPassword) {
        return res.status(400).json({
            success: false,
            message: '현재 비밀번호와 새 비밀번호를 모두 입력해주세요.'
        });
    }

    try {
        // 2. 기존 사용자 정보 조회
        const [rows] = await pool.query(
            'SELECT password_hash FROM users WHERE id = ?', 
            [userId]
        );

        if (rows.length === 0) {
            return res.status(404).json({
                success: false,
                message: '사용자를 찾을 수 없습니다.'
            });
        }

        const user = rows[0];

        // 3. 현재 비밀번호 일치하는지 검사
        const isMatch = await bcrypt.compare(
            currentPassword, 
            user.password_hash
        );

        if (!isMatch) {
            return res.status(401).json({
                success: false,
                message: '현재 비밀번호가 올바르지 않습니다.'
            });
        }

        // 4. 새로운 비밀번호 해시 생성
        const saltRounds = 10;
        const newHashedPassword = await bcrypt.hash(newPassword, saltRounds);

        // 5. DB 업데이트
        await pool.query(
            'UPDATE users SET password_hash = ? WHERE id = ?',
            [newHashedPassword, userId]
        );

        console.log(`[비밀번호 변경 성공] userId=${userId}`);

        return res.status(200).json({
            success: true,
            message: '비밀번호가 성공적으로 변경되었습니다.'
        });

    } catch (error) {
        console.error('[서버 오류] 비밀번호 변경 중 에러:', error);
        return res.status(500).json({
            success: false,
            message: '서버 내부 오류가 발생했습니다.'
        });
    }
};

exports.changeNickname = async (req, res) => {
    const userId = req.user?.userId; // authMiddleware에서 담아준 값
    const { newNickname } = req.body;

    console.log(`[닉네임 변경 시도] userId=${userId}, newNickname=${newNickname}`);

    // 1. 입력 값 확인
    if (!newNickname || newNickname.trim() === "") {
        return res.status(400).json({
            success: false,
            message: "새 닉네임을 입력해주세요."
        });
    }

    try {
        // 2. 닉네임 중복 검사
        const [existing] = await pool.query(
            "SELECT id FROM users WHERE nickname = ? AND id != ?",
            [newNickname, userId]
        );

        if (existing.length > 0) {
            return res.status(409).json({
                success: false,
                message: "이미 사용 중인 닉네임입니다."
            });
        }

        // 3. 닉네임 업데이트
        await pool.query(
            "UPDATE users SET nickname = ? WHERE id = ?",
            [newNickname, userId]
        );

        console.log(`[닉네임 변경 성공] userId=${userId} → ${newNickname}`);

        return res.status(200).json({
            success: true,
            message: "닉네임이 성공적으로 변경되었습니다.",
            newNickname: newNickname
        });

    } catch (error) {
        console.error("[서버 오류] 닉네임 변경 중 에러:", error);
        return res.status(500).json({
            success: false,
            message: "서버 내부 오류가 발생했습니다."
        });
    }
};

exports.updateProfileImage = async (req, res) => {
    try {
        // 함수 안에 넣지 않고 '기다려(await)'라고 시킴
        await uploadPromise(req, res); 

        if (!req.file) return res.status(400).send("파일 없음");

        const userId = req.user.userId;
        const imageUrl = `/profile/${req.file.filename}`;

        await pool.query("UPDATE users SET profile_image_url = ? WHERE id = ?", [imageUrl, userId]);

        return res.status(200).json({ success: true, profileImageUrl: imageUrl });
    } catch (err) {
        return res.status(500).send("에러 발생");
    }
};

exports.getMe = async (req, res) => {
    const userId = req.user.userId; // authMiddleware에서 검증된 ID
    const includePhoto = req.query.includeProfileImage === 'true';

    try {
        const sql = `
            SELECT id, email, nickname, profile_image_url, create_at
            FROM users 
            WHERE id = ?
        `;
        const [rows] = await pool.query(sql, [userId]);

        if (rows.length === 0) {
            return res.status(404).json({ success: false, message: '유저를 찾을 수 없습니다.' });
        }

        const user = rows[0];
        let photoData = null;

        if (includePhoto && user.profile_image_url) {
            try {
                const filePath = path.join(__dirname, '..', 'storage', 'profile', path.basename(user.profile_image_url));
                const imageBuffer = await fs.readFile(filePath);
                photoData = `data:image/jpeg;base64,${imageBuffer.toString('base64')}`;
            } catch (fileErr) {
                console.error("사진 파일 읽기 실패:", fileErr);
                photoData = null; 
            }
        }

        res.status(200).json({
            success: true,
            user: {
                id: user.id,
                email: user.email,
                nickname: user.nickname,
                createAt: user.create_at,
                // 요청했을 때만 데이터가 담기고, 아니면 null이 나감
                profileImageBase64: photoData
            }
        });
    } catch (error) {
        console.error(error);
        res.status(500).json({ success: false, message: '서버 오류' });
    }
};

/*
추가할만한 Endpoints
1. 계정 탈퇴
2. 닉네임 중복 확인
*/
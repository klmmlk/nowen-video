package service

import (
	"io/fs"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"go.uber.org/zap"
)

// fakeRemoteFS 模拟远程存储：dirs 中的路径视为目录，其余不存在
type fakeRemoteFS struct {
	dirs map[string]bool
}

func (f *fakeRemoteFS) Type() string { return "alist" }

func (f *fakeRemoteFS) Stat(p string) (fs.FileInfo, error) {
	if f.dirs[p] {
		return &fakeRemoteFileInfo{name: filepath.Base(p), isDir: true}, nil
	}
	return nil, fs.ErrNotExist
}

func (f *fakeRemoteFS) Open(p string) (VFSFile, error)         { return nil, fs.ErrNotExist }
func (f *fakeRemoteFS) ReadDir(p string) ([]fs.DirEntry, error) { return nil, nil }
func (f *fakeRemoteFS) Walk(root string, fn filepath.WalkFunc) error {
	return nil
}

type fakeRemoteFileInfo struct {
	name  string
	isDir bool
}

func (f *fakeRemoteFileInfo) Name() string       { return f.name }
func (f *fakeRemoteFileInfo) Size() int64        { return 0 }
func (f *fakeRemoteFileInfo) Mode() fs.FileMode  { return fs.ModeDir | 0555 }
func (f *fakeRemoteFileInfo) ModTime() time.Time { return time.Time{} }
func (f *fakeRemoteFileInfo) IsDir() bool        { return f.isDir }
func (f *fakeRemoteFileInfo) Sys() interface{}   { return nil }

func TestStatLibraryPath_RemotePath(t *testing.T) {
	s := &LibraryService{vfsMgr: NewVFSManager(zap.NewNop().Sugar()), logger: zap.NewNop().Sugar()}
	s.vfsMgr.SetGlobalAlistFS(&fakeRemoteFS{dirs: map[string]bool{"/电影": true}})

	// 远端存在的目录：不报错
	if _, err := s.statLibraryPath("alist://电影"); err != nil {
		t.Fatalf("alist:// 已存在目录应通过预检，得到: %v", err)
	}

	// 远端不存在的目录：报错
	_, err := s.statLibraryPath("alist://不存在")
	if err == nil {
		t.Fatal("alist:// 不存在的目录应报错")
	}

	// 对应远程存储未注入（S3 未启用，回退 LocalFS）：报明确错误
	_, err = s.statLibraryPath("s3://bucket/media")
	if err == nil || !strings.Contains(err.Error(), "远程存储未启用或初始化失败") {
		t.Fatalf("未启用的远程存储应报\"远程存储未启用或初始化失败\"，得到: %v", err)
	}
}

func TestStatLibraryPath_RemoteWithoutVFSManager(t *testing.T) {
	s := &LibraryService{logger: zap.NewNop().Sugar()}
	if _, err := s.statLibraryPath("alist://电影"); err != nil {
		t.Fatalf("vfsMgr 为 nil 时应放行远程路径，得到: %v", err)
	}
}

func TestStatLibraryPath_LocalPath(t *testing.T) {
	s := &LibraryService{vfsMgr: NewVFSManager(zap.NewNop().Sugar()), logger: zap.NewNop().Sugar()}

	dir := t.TempDir()
	if _, err := s.statLibraryPath(dir); err != nil {
		t.Fatalf("本地存在的目录应通过预检，得到: %v", err)
	}

	if _, err := s.statLibraryPath(filepath.Join(dir, "not-exist")); err == nil {
		t.Fatal("本地不存在的路径应报错")
	}

	file := filepath.Join(dir, "a.txt")
	if err := os.WriteFile(file, []byte("x"), 0644); err != nil {
		t.Fatal(err)
	}
	_, err := s.statLibraryPath(file)
	if err == nil || !strings.Contains(err.Error(), "路径不是目录") {
		t.Fatalf("本地文件应报\"路径不是目录\"，得到: %v", err)
	}
}

func TestBroadcastScanFailed_ClearsPhaseState(t *testing.T) {
	s := &LibraryService{logger: zap.NewNop().Sugar()} // wsHub 为 nil
	s.scanStates.Store("lib-1", &ScanPhaseData{LibraryID: "lib-1"})

	s.broadcastScanFailed("lib-1", "测试库", "媒体库路径不可访问")

	if _, ok := s.scanStates.Load("lib-1"); ok {
		t.Fatal("广播扫描失败后应清除该库的扫描阶段状态")
	}
}
